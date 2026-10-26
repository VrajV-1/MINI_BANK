@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
set SOURCES=
for /r src\main\java %%f in (*.java) do set SOURCES=!SOURCES! "%%f"
javac --release 17 -d out !SOURCES!
if errorlevel 1 exit /b 1
jar --create --file MiniBank.jar --main-class minibank.MiniBank -C out .
if errorlevel 1 exit /b 1
echo Built MiniBank.jar. Run it with: java -jar MiniBank.jar
