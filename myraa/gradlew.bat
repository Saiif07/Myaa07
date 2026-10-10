@echo off
rem MYRAA gradlew.bat: uses gradle-wrapper.jar when present, otherwise downloads Gradle 8.7 by itself.
setlocal
set "APP_HOME=%~dp0"
set "JAVACMD=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVACMD=%JAVA_HOME%\bin\java.exe"
set "JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"
if exist "%JAR%" (
  "%JAVACMD%" -Dorg.gradle.appname=gradlew -classpath "%JAR%" org.gradle.wrapper.GradleWrapperMain %*
  exit /b %ERRORLEVEL%
)
set "VER=8.7"
set "BASE=%USERPROFILE%\.gradle\myraa-dist"
set "DIST=%BASE%\gradle-%VER%"
if not exist "%DIST%\bin\gradle.bat" (
  if not exist "%BASE%" mkdir "%BASE%"
  echo gradlew: downloading Gradle %VER% one time ...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%VER%-bin.zip' -OutFile '%BASE%\gradle.zip'; Expand-Archive -Force '%BASE%\gradle.zip' '%BASE%'"
  if errorlevel 1 (
    echo gradlew: download failed
    exit /b 1
  )
)
call "%DIST%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
