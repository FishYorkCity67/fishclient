# Prints the media session Windows reports as one JSON line per second. Ends by itself when the game is gone.
$ErrorActionPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

Add-Type -AssemblyName System.Runtime.WindowsRuntime
Add-Type -AssemblyName System.Drawing
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.IRandomAccessStream, Windows.Storage.Streams, ContentType = WindowsRuntime]

$asStream = [System.IO.WindowsRuntimeStreamExtensions].GetMethod('AsStream', [Type[]]@([Windows.Storage.Streams.IRandomAccessStream]))
$asTask =([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await($operation, [Type]$resultType) {
    $task = $asTask.MakeGenericMethod($resultType).Invoke($null, @($operation))
    [void]$task.Wait(3000)
    return $task.Result
}

$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
$propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
$streamType = [Windows.Storage.Streams.IRandomAccessStreamWithContentType]
$manager = Await ($managerType::RequestAsync()) $managerType

$coverDir = Join-Path $env:TEMP 'fishclient-covers'
[void](New-Item -ItemType Directory -Force $coverDir)
$lastKey = ''
$coverPath = ''
$coverIndex = 0
$coverTries = 0
$gamePid = [int]$env:FISHCLIENT_PID

while ($true) {
    if ($gamePid -gt 0 -and -not (Get-Process -Id $gamePid -ErrorAction SilentlyContinue)) { break }

    $track = @{ playing = $false; title = ''; artist = ''; app = ''; cover = ''; positionMs = 0; durationMs = 0 }
    $session = $manager.GetCurrentSession()
    if ($session) {
        $props = Await ($session.TryGetMediaPropertiesAsync()) $propsType
        $timeline = $session.GetTimelineProperties()
        # 4 is GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing
        $track.playing = ([int]$session.GetPlaybackInfo().PlaybackStatus -eq 4)
        $track.app = $session.SourceAppUserModelId
        if ($props) {
            $track.title = $props.Title
            $track.artist = $props.Artist
            $key = "$($props.Title)|$($props.Artist)"
            if ($key -ne $lastKey) {
                $lastKey = $key
                $coverPath = ''
                $coverTries = 0
            }
            # Apps often deliver the cover a moment after the title, so a missing one is tried again a few times.
            if (-not $coverPath -and $props.Thumbnail -and $coverTries -lt 8) {
                $coverTries++
                try {
                    # PowerShell sees only a bare COM object here, so the conversion has to go through reflection.
                    $winStream = Await ($props.Thumbnail.OpenReadAsync()) $streamType
                    $net = $asStream.Invoke($null, @($winStream))
                    $image = [System.Drawing.Image]::FromStream($net)
                    $bitmap = New-Object System.Drawing.Bitmap 128, 128
                    $canvas = [System.Drawing.Graphics]::FromImage($bitmap)
                    $canvas.InterpolationMode = 'HighQualityBicubic'
                    $canvas.DrawImage($image, 0, 0, 128, 128)
                    $coverIndex = ($coverIndex + 1) % 4
                    $coverPath = Join-Path $coverDir "cover$coverIndex.png"
                    $bitmap.Save($coverPath, [System.Drawing.Imaging.ImageFormat]::Png)
                    $canvas.Dispose(); $bitmap.Dispose(); $image.Dispose(); $net.Dispose()
                } catch { $coverPath = '' }
            }
            $track.cover = $coverPath
        }
        if ($timeline) {
            $position = $timeline.Position.TotalMilliseconds
            $track.durationMs = [long]$timeline.EndTime.TotalMilliseconds
            # Apps only report the position now and then, so it is counted forward from that moment while playing.
            if ($track.playing) {
                $position += ([DateTimeOffset]::Now - $timeline.LastUpdatedTime).TotalMilliseconds
            }
            $track.positionMs = [long][math]::Min($position, $track.durationMs)
        }
    }
    $track | ConvertTo-Json -Compress
    Start-Sleep -Seconds 1
}
