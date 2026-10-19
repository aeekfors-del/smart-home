$env:JAVA_HOME = "C:\Users\Besenok\.jdks\jbr-21.0.11"
$env:JAVA_HOME = $env:JAVA_HOME.Replace("\", "/")
$gradlePath = "C:\Users\Besenok\.gradle\wrapper\dists\gradle-9.6.0-bin\42k10rwplmzkhuboz9kdazi7s\gradle-9.6.0\bin\gradle.bat"

Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "Gradle path: $gradlePath"

& $gradlePath $args
