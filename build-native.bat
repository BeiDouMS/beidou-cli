@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo  beidou native-image build script
echo ============================================================

REM === 1. Locate MSVC toolchain (Windows only) ==============================
if defined VCINSTALLDIR (
    echo [OK] MSVC environment already set up
    goto :find_java
)

echo Looking for Visual Studio installation...

REM Find vswhere.exe
set "VSWHERE="
for /f "usebackq delims=" %%i in (`where vswhere 2^>nul`) do set "VSWHERE=%%i"
if not defined VSWHERE (
    if exist "C:\Program Files (x86)\Microsoft Visual Studio\Installer\vswhere.exe" (
        set "VSWHERE=C:\Program Files (x86)\Microsoft Visual Studio\Installer\vswhere.exe"
    )
)
if not defined VSWHERE (
    if exist "C:\Program Files\Microsoft Visual Studio\Installer\vswhere.exe" (
        set "VSWHERE=C:\Program Files\Microsoft Visual Studio\Installer\vswhere.exe"
    )
)

if not defined VSWHERE (
    echo [FAIL] Cannot find vswhere.exe.
    echo        Please run from "x64 Native Tools Command Prompt for VS".
    pause
    exit /b 1
)

for /f "usebackq delims=" %%i in (`"%VSWHERE%" -latest -property installationPath`) do set "VS_PATH=%%i"
if not defined VS_PATH (
    echo [FAIL] No Visual Studio installation found.
    pause
    exit /b 1
)
echo [OK] Found VS at: %VS_PATH%

set "VCDIR=%VS_PATH%\VC\Auxiliary\Build"
if exist "%VCDIR%\vcvarsall.bat" (
    call "%VCDIR%\vcvarsall.bat" x64 > nul
    echo [OK] MSVC environment initialized
) else (
    echo [FAIL] vcvarsall.bat not found in %VCDIR%
    pause
    exit /b 1
)

REM === 2. Locate GraalVM JDK ========================================================
:find_java

REM User override
if defined GRAALVM_HOME (
    set "JAVA_HOME=%GRAALVM_HOME%"
    goto :java_ready
)

REM Check if current JAVA_HOME is GraalVM
if defined JAVA_HOME (
    "%JAVA_HOME%\bin\java" -version 2>&1 | findstr /c:"GraalVM" > nul
    if not errorlevel 1 (
        echo [OK] Using JAVA_HOME: %JAVA_HOME%
        goto :java_ready
    )
)

REM Scan common locations for GraalVM JDK
echo Searching for GraalVM JDK...
set "JAVA_HOME="

REM Check sibling of current JAVA_HOME first (often same parent dir)
set "SEARCH_JAVA=%JAVA_HOME%"
if not defined JAVA_HOME (
    if defined SEARCH_JAVA (
        for %%d in ("%SEARCH_JAVA%\..") do set "JDK_PARENT=%%~fd"
        if exist "%JDK_PARENT%\" (
            for /f "usebackq delims=" %%i in (`dir /b "%JDK_PARENT%\graalvm-*" 2^>nul`) do (
                if not defined JAVA_HOME set "JAVA_HOME=%JDK_PARENT%\%%i"
            )
        )
    )
)

REM Check C:\Program Files\Java and D:\Program Files\Java (both common)
if not defined JAVA_HOME (
    for %%d in (C D) do (
        if not defined JAVA_HOME (
            if exist "%%d:\Program Files\Java\" (
                for /f "usebackq delims=" %%i in (`dir /b "%%d:\Program Files\Java\graalvm-*" 2^>nul`) do (
                    if not defined JAVA_HOME set "JAVA_HOME=%%d:\Program Files\Java\%%i"
                )
            )
        )
    )
)

REM If still not found, try GRAALVM_HOME env var
if not defined JAVA_HOME (
    if defined GRAALVM_HOME (
        if exist "%GRAALVM_HOME%\bin\java.exe" (
            set "JAVA_HOME=%GRAALVM_HOME%"
        )
    )
)

if not defined JAVA_HOME (
    echo [FAIL] No GraalVM JDK found.
    echo        Set GRAALVM_HOME=D:\Path\To\graalvm-jdk and retry.
    pause
    exit /b 1
)

:java_ready
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [FAIL] java.exe not found in %JAVA_HOME%\bin
    pause
    exit /b 1
)
echo [OK] JAVA_HOME = %JAVA_HOME%

REM Ensure native-image is on PATH
set "PATH=%JAVA_HOME%\bin;%JAVA_HOME%\lib\svm\bin;%PATH%"
where native-image > nul 2>&1
if errorlevel 1 (
    echo [FAIL] native-image not found. Reinstall GraalVM with native-image.
    pause
    exit /b 1
)

REM === 3. Build =====================================================================
echo.
echo Building native image for beidou...
echo.
mvn -f "%~dp0pom.xml" package -DskipTests -Pnative
if %ERRORLEVEL% neq 0 (
    echo.
    echo [FAIL] Build failed.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo  Build successful: %~dp0target\beidou.exe
echo ============================================================
endlocal
\r