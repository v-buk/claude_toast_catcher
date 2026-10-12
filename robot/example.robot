*** Settings ***
Library     ToastCatcher.py    serial=${SERIAL}

*** Variables ***
${SERIAL}    ${EMPTY}    # leave empty if only one device is connected

*** Test Cases ***
One Time Phone Setup
    [Documentation]    Run once per phone after installing the APK.
    Enable Toast Service
    ${status}=    Toast Capture Status
    Should Be Equal    ${status}[service_enabled]    true

Wait For A PCTS Result Toast
    Start Toast Capture
    # ... drive the PCTS app here (tap Run, etc.) ...
    ${toast}=    Wait For Toast    Test (Passed|Failed)    timeout=300
    Log    PCTS said: ${toast}[text] at ${toast}[time]
    Stop Toast Capture

Check Everything That Happened
    Start Toast Capture
    # ... run a test ...
    Stop Toast Capture
    ${toasts}=    Get Toasts And Clear
    FOR    ${t}    IN    @{toasts}
        Log    ${t}[time] [${t}[package]] ${t}[text]
    END
    ${texts}=    Evaluate    [t['text'] for t in $toasts]
    Should Not Contain    ${texts}    Vehicle disconnected
