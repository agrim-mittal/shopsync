@echo off
REM Compile and run ShopSync on Windows. Requires JDK 11 or newer.
cd /d "%~dp0"
if exist out rmdir /s /q out
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
if errorlevel 1 (
  del sources.txt
  pause
  exit /b 1
)
del sources.txt
java -Dfile.encoding=UTF-8 -cp out com.shopsync.Main %*
pause
