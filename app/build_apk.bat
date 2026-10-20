@echo off
REM WOL Native APK build script (no Gradle)
setlocal enabledelayedexpansion

set JDK=D:\deepseek\work2\android-tools\jdk\jdk-17.0.20.1+1
set SDK=D:\deepseek\work2\android-tools\sdk
set BT=%SDK%\build-tools\34.0.0
set PLATFORM=%SDK%\platforms\android-34\android.jar
set PROJ=D:\deepseek\work2\wol-native
set OUT=%PROJ%\build
set JAVAC=%JDK%\bin\javac.exe
set JAR=%JDK%\bin\jar.exe
set KEYTOOL=%JDK%\bin\keytool.exe

echo ============================================
echo  WOL Native APK build
echo ============================================

if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"
mkdir "%OUT%\gen"
mkdir "%OUT%\classes"
mkdir "%OUT%\dex"

echo [1/7] compile resources
"%BT%\aapt2.exe" compile --dir "%PROJ%\app\src\main\res" -o "%OUT%\res.zip"
if errorlevel 1 goto fail

echo [2/7] link resources
"%BT%\aapt2.exe" link -o "%OUT%\app-base.apk" -I "%PLATFORM%" --manifest "%PROJ%\app\src\main\AndroidManifest.xml" --java "%OUT%\gen" --min-sdk-version 24 --target-sdk-version 34 --version-code 1 --version-name "1.0" "%OUT%\res.zip"
if errorlevel 1 goto fail

echo [3/7] compile java
set SRCLIST=
for /r "%PROJ%\app\src\main\java" %%f in (*.java) do set SRCLIST=!SRCLIST! "%%f"
for /r "%OUT%\gen" %%f in (*.java) do set SRCLIST=!SRCLIST! "%%f"
"%JAVAC%" -source 8 -target 8 -encoding UTF-8 -classpath "%PLATFORM%" -d "%OUT%\classes" !SRCLIST!
if errorlevel 1 goto fail

echo [4/7] d8 dex
set CLASSLIST=
for /r "%OUT%\classes" %%f in (*.class) do set CLASSLIST=!CLASSLIST! "%%f"
call "%BT%\d8.bat" --lib "%PLATFORM%" --min-api 24 --release --output "%OUT%\dex" !CLASSLIST!
if errorlevel 1 goto fail

echo [5/7] package apk
copy /Y "%OUT%\app-base.apk" "%OUT%\app-unsigned.apk" >nul
"%JAR%" uf "%OUT%\app-unsigned.apk" -C "%OUT%\dex" classes.dex
if errorlevel 1 goto fail

echo [6/7] zipalign
"%BT%\zipalign.exe" -f 4 "%OUT%\app-unsigned.apk" "%OUT%\app-aligned.apk"
if errorlevel 1 goto fail

echo [7/7] sign
if not exist "%PROJ%\debug.keystore" (
    "%KEYTOOL%" -genkeypair -v -keystore "%PROJ%\debug.keystore" -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >nul 2>&1
)
call "%BT%\apksigner.bat" sign --ks "%PROJ%\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "%OUT%\wol-native.apk" "%OUT%\app-aligned.apk"
if errorlevel 1 goto fail

echo.
echo ============================================
echo  BUILD SUCCESS
echo ============================================
dir "%OUT%\wol-native.apk"
goto end

:fail
echo.
echo ============================================
echo  BUILD FAILED
echo ============================================
exit /b 1

:end
