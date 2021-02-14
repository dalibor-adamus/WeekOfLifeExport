# Week Details Downloader
API provides REST services that can be used to download details of weeks 
from the server and store them in SQLite database. It can be then used
to e.g. search for a specific word used in day description to get date 
of event or export data to HTML.

## Build API
Use following command to build runnable for HotSpot:
`./mvnw install`

Use following command to build native runnable for GraalVM:
`./mvnw install -Pnative`

Following executable file will be created in `target` directory:
`week-detail-downloader-1.0.0-runner.jar`

If you execute it, you can then point your browser to `http://localhost:8080/q/swagger/`

## Run API







