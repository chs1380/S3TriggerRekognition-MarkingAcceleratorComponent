ECHO Compile
CALL sbt compile
ECHO Assembly
CALL sbt assembly
ECHO UPLOAD JAR
aws s3 cp E:\Working\RekognitionLambda\target\scala-2.12\RekognitionLambda-assembly-1.0.jar s3://markingaccelerator-cloudlabhk-com
ECHO UPDATE LAMBDA
CALL aws lambda update-function-code --function-name RekognitionLambda --s3-bucket markingaccelerator-cloudlabhk-com --s3-key RekognitionLambda-assembly-1.0.jar
ECHO UPLOAD Image
aws s3 cp C:\Users\developer\Downloads\a.jpg s3://rekognition.cloudlabhk.com

