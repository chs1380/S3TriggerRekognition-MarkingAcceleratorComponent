package com.cloudlabhk.rekognition

import java.net.URLDecoder

import com.amazonaws.services.lambda.runtime.events.S3Event
import com.amazonaws.services.rekognition.model._
import com.amazonaws.services.rekognition.{AmazonRekognition, AmazonRekognitionClient}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ListObjectsV2Request
import com.google.gson.Gson

import scala.collection.JavaConverters._


class Rekognition {
  def processImage(event: S3Event): String = {

    val sourceKey = event.getRecords.asScala.map(record => decodeS3Key(record.getS3.getObject.getKey)).head
    val bucket = event.getRecords.asScala.map(record => decodeS3Key(record.getS3.getBucket.getName)).head

    println("Source:" + bucket + "/" + sourceKey)

    if (sourceKey.endsWith(".json")) {
      println("It is json!")
      return "OK"
    }

    val s3Client = new AmazonS3Client

    val rekognitionClient = new AmazonRekognitionClient()
    rekognitionClient.withEndpoint("rekognition.us-east-1.amazonaws.com")
    rekognitionClient.setSignerRegionOverride("us-east-1")

    val request = new DetectLabelsRequest()
      .withImage(new Image()
        .withS3Object(new S3Object()
          .withName(sourceKey)
          .withBucket(bucket)))
      .withMaxLabels(10)
      .withMinConfidence(77F)

    val factRequest = new DetectFacesRequest()
      .withImage(new Image()
        .withS3Object(new S3Object()
          .withName(sourceKey)
          .withBucket(bucket)))
      .withAttributes(Attribute.ALL)

    try {
      val result = rekognitionClient.detectLabels(request)
      val factResult = rekognitionClient.detectFaces(factRequest)
      println("object = " + result)
      println("face = " + result)
      val gson = new Gson
      s3Client.putObject(bucket, "object.json", gson.toJson(result))
      s3Client.putObject(bucket, "face.json", gson.toJson(factResult))

      println("Listing objects")
      val req = new ListObjectsV2Request().withBucketName(bucket).withPrefix("people/").withMaxKeys(100)
      val keys = s3Client.listObjectsV2(req).getObjectSummaries().asScala.map(_.getKey)
      val comparisons = keys.filter(_.contains(".")).map(k => {
        println(k)
        val source = getImageUtil(bucket, k)
        val target = getImageUtil(bucket, sourceKey)
        val similarityThreshold = 70F
        val compareFacesResult = callCompareFaces(source, target, similarityThreshold, rekognitionClient)
        (k, compareFacesResult)
      })

      val face = comparisons
        .filter(d => d._2.getFaceMatches.size() == 1)
        .map(f => (f._1, f._2.getFaceMatches.get(0).getSimilarity))
      if (face.length == 1) {
        s3Client.putObject(bucket, "compare.json", "{\"user\":\"" + face.head._1.replace("people/", "") + "\",\"similarity\":" + face.head._2 + "}")
      } else {
        s3Client.putObject(bucket, "compare.json", "{\"user\":\"No Match\",\"similarity\":0}")
      }

    }

    catch {
      case e: AmazonRekognitionException => e.printStackTrace()
    }
    "OK"
  }

  def callCompareFaces(sourceImage: Image, targetImage: Image, similarityThreshold: Float, amazonRekognition: AmazonRekognition) = {
    val compareFacesRequest = new CompareFacesRequest()
      .withSourceImage(sourceImage)
      .withTargetImage(targetImage)
      .withSimilarityThreshold(similarityThreshold)
    amazonRekognition.compareFaces(compareFacesRequest)
  }

  def getImageUtil(bucket: String, key: String): Image = {
    new Image().withS3Object(new S3Object().withBucket(bucket).withName(key))
  }

  def decodeS3Key(key: String): String = URLDecoder.decode(key.replace("+", " "), "utf-8")
}
