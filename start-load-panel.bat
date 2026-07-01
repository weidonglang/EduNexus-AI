@echo off
cd /d "%~dp0"

echo Project directory:
cd
echo.

if defined PYTHON_EXE (
    set "PY_CMD="%PYTHON_EXE%""
) else (
    where py >nul 2>nul
    if not errorlevel 1 (
        set "PY_CMD=py -3"
    ) else (
        set "PY_CMD=python"
    )
)

echo Python command:
echo %PY_CMD%
echo.

%PY_CMD% --version
if errorlevel 1 (
    echo.
    echo ERROR: Python executable not found.
    echo Set PYTHON_EXE to your python.exe path, or install Python and add it to PATH.
    pause
    exit /b 1
)
echo.

echo Testing tkinter...
%PY_CMD% -c "import tkinter; print('tkinter OK')"

if errorlevel 1 (
    echo.
    echo ERROR: tkinter is not available in this Python.
    pause
    exit /b 1
)

echo.
echo Starting panel...
%PY_CMD% "%~dp0scripts\course_grab_panel.py"

echo.
echo Program exited. Error code: %errorlevel%
pause
