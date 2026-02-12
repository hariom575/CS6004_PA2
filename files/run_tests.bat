@echo off
REM Redundant Load Analysis - Test Suite Runner (Windows)

echo ==========================================
echo Redundant Load Analysis - Test Suite
echo ==========================================
echo.

REM Check if soot jar exists
if not exist "soot-4.6.0-jar-with-dependencies.jar" (
    echo Error: soot-4.6.0-jar-with-dependencies.jar not found
    echo Please download it from: https://github.com/soot-oss/soot/releases
    exit /b 1
)

REM Compile PA2.java if needed
if not exist "PA2.class" (
    echo Compiling PA2.java...
    javac -cp ".;soot-4.6.0-jar-with-dependencies.jar" PA2.java
    if errorlevel 1 (
        echo Failed to compile PA2.java
        exit /b 1
    )
    echo PA2.java compiled successfully
    echo.
)

REM Function to setup and run a test case
call :run_test TestCase1 Test1
call :run_test TestCase2 Test2
call :run_test TestCase3 Test3
call :run_test TestCase4 Test4
call :run_test TestCase5 Test5
call :run_test TestCase6 Test6
call :run_test TestCase7 Test7
call :run_test TestCase8 Test8
call :run_test TestCase9 Test9
call :run_test TestCase10 Test10

echo ==========================================
echo All tests completed!
echo ==========================================
echo.
echo Test directories created: Test1 through Test10
echo To run individual tests:
echo   java -cp ".;soot-4.6.0-jar-with-dependencies.jar" PA2 Test1
echo.
echo To clean up:
echo   rmdir /s /q Test1 Test2 Test3 Test4 Test5 Test6 Test7 Test8 Test9 Test10
goto :eof

:run_test
setlocal
set testname=%1
set testdir=%2

echo ======================================
echo Running: %testname%
echo ======================================

REM Create test directory
if not exist "%testdir%" mkdir "%testdir%"

REM Copy test file
if exist "%testname%.java" (
    copy "%testname%.java" "%testdir%\Test.java" >nul
) else (
    echo Warning: %testname%.java not found, skipping...
    goto :eof
)

REM Compile test case
echo Compiling %testdir%\Test.java...
cd "%testdir%"
javac -g Test.java 2>nul
if errorlevel 1 (
    echo Failed to compile %testdir%\Test.java
    cd ..
    goto :eof
)
cd ..

REM Run analysis
echo Running analysis on %testdir%...
java -cp ".;soot-4.6.0-jar-with-dependencies.jar" PA2 "%testdir%" 2>nul

echo.
endlocal
goto :eof
