param(
    [Parameter(Mandatory=$true)][string]$LessonFile,
    [string]$ConfigFile="$env:LOCALAPPDATA\SpeakFlow\config\database.env"
)
$ErrorActionPreference='Stop'
# Only send the application login to this fixed loopback destination.
$base='http://127.0.0.1:8080/api/v1'
$config=@{}
foreach($line in Get-Content -LiteralPath $ConfigFile){
    if($line.Trim() -eq '' -or $line.TrimStart().StartsWith('#')){continue}
    $pair=$line -split '=',2
    if($pair.Count -eq 2){$config[$pair[0]]=$pair[1]}
}
$json=Get-Content -LiteralPath $LessonFile -Raw -Encoding utf8
$lesson=$json | ConvertFrom-Json
if(!$lesson.date -or !$lesson.content -or !$lesson.planId){throw 'Missing date, content or planId'}
$csrf=Invoke-RestMethod "$base/auth/csrf" -SessionVariable session
$login=@{username=$config.APP_ADMIN_USERNAME;password=$config.APP_ADMIN_PASSWORD}|ConvertTo-Json
$null=Invoke-RestMethod "$base/auth/login" -Method Post -WebSession $session -Headers @{'X-XSRF-TOKEN'=$csrf.token} -ContentType 'application/json' -Body $login
try {
    $csrf=Invoke-RestMethod "$base/auth/csrf" -WebSession $session
    $result=Invoke-RestMethod "$base/admin/import-lesson" -Method Post -WebSession $session -Headers @{'X-XSRF-TOKEN'=$csrf.token} -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json))
    $saved=Invoke-RestMethod "$base/lessons/$($result.lessonId)" -WebSession $session
    if($saved.date -ne $lesson.date){throw 'Imported lesson date verification failed'}
    $result | ConvertTo-Json -Compress
} finally {
    try {$null=Invoke-RestMethod "$base/auth/logout" -Method Post -WebSession $session -Headers @{'X-XSRF-TOKEN'=$csrf.token}} catch {}
}
