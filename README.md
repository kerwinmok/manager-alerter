# doorway alert

a local person alert for a doorway

it does not identify people, save video, or send camera frames online

use it where people know the camera is on

## download on windows

open the actions tab on github and choose the latest `windows app` run

download the `DoorwayAlert-setup` artifact and unzip it

run the setup exe and follow the steps

no java or maven needed on the computer that runs the download

## build it yourself

- java 21
- maven
- a camera

run `powershell -ExecutionPolicy Bypass -File ./package-windows.ps1`

## run in a terminal

run `mvn exec:java`

choose a camera number and start monitoring

drag over the doorway to watch only that area

the app stays visible while running and stops the camera when closed
