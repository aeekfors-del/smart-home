$env:JAVA_HOME = "C:\Users\Besenok\.jdks\jbr-21.0.11"
$env:JAVA_HOME = $env:JAVA_HOME.Replace("\", "/")
$gradlePath = "C:\Users\Besenok\.gradle\wrapper\dists\gradle-8.14.5-bin\690y85m0j9nfaub7xoiayko8a\gradle-8.14.5\bin\gradle.bat"

Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "Gradle path: $gradlePath"

& $gradlePath $args
