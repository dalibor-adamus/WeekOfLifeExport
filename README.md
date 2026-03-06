# Week Details Downloader
API provides REST services that can be used to download details of weeks 
from the server and store them in SQLite database. It can be then used
to e.g. search for a specific word used in day description to get date 
of event or export data to HTML.

## Build API
Use following command to build the application:
`./gradlew build`

Following executable file will be created in `build/libs` directory:
`wol-export-1.0.0.jar`

If you execute it, you can then point your browser to `http://localhost:8080/swagger-ui/index.html`

## Run API

Run API via IntelliJ or from command line:
`./gradlew bootRun`

Swagger-UI can be then found on localhost after started:
`http://localhost:8080/swagger-ui/index.html`

You should see following log:
`Listening on: http://localhost:8080`

## Services

### Editor Choices
`/week-of-life/editors-choices` - download all editor choices to database file EditorsChoice.db

`/week-of-life/editors-choices/exports` - exports editor choices for specific user to HTML format

`/week-of-life/editors-choices/statistics` - statistics of editor choices per user from the highest number of editor choices to the lowest


### Photo of the Day
`/week-of-life/photo-of-the-day` - download all photos of the day to database file PhotoOfTheDay.db

`/week-of-life/photo-of-the-day/exports` - export photos of the day for specified user to HTML format

`/week-of-life/photo-of-the-day/statistics` - statistics of photo of the day per user

### Weeks
`/week-of-life/weeks` - parse weeks from Week of Life and store them together with days to local database <user name>.db. It will go from the newest weeks until end or until some parsed week is already stored

`/week-of-life/weeks/exports` - export week details to HTML format 







