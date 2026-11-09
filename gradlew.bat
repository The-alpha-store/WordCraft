@echo off
setlocal
set GRADLE_VERSION=8.9
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set INSTALL_DIR=%GRADLE_USER_HOME%\wordcraft-gradle-%GRADLE_VERSION%
set GRADLE_BIN=%INSTALL_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat
if not exist "%GRADLE_BIN%" (
  if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%INSTALL_DIR%\gradle.zip'; Expand-Archive -Force '%INSTALL_DIR%\gradle.zip' '%INSTALL_DIR%'; Remove-Item '%INSTALL_DIR%\gradle.zip'"
)
call "%GRADLE_BIN%" %*
endlocal
