# doorway alert

local person alerts for a doorway. no identification, video storage, or camera uploads.

use where people know the camera is on.

## windows

download the installer artifact from the latest `windows app` run and run setup. java and maven are not needed.

## build

requires java 21, maven, and a camera.

run `powershell -executionpolicy bypass -file ./package-windows.ps1`

or run `mvn exec:java`.

## alerts

adjust ding volume and cooldown (1 to 300 seconds) in the controls. use `test sound` to check audio.

## detection

the detector works best when people appear upright. rotate the view if needed.

raise sensitivity to catch more people. disable `ignore things that don't move` if detections are still missed.
