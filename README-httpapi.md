# Open Liberty Runtime HTTP API

When deployed as OSGi bundles on Domino, the runtime provides a small HTTP API for managing applications. Currently, the API contains the following endpoints:

## App Deployment

`POST /org.openntf.openliberty.domino/admin/deployapp`

Parameters:
- Header `X-ServerName`: the name of an existing server document
- Header `X-AppName`: the name of an existing or new app to deploy
- Header `X-ContextPath`: the context path of the app, without the preceding `/`
- Header `X-FileName`: a filename hint for the file data, such as "appname.war"
- Header `X-IncludeInReverseProxy`: a boolean `true` or `false` indicating whether the app should participate in reverse proxy services
- POST body should contain the app data, such as the bytes of a WAR file

This will deploy a new or updated app to an existing server. The authenticated user must have the `[DeployApp]` role in the libertyadmin.nsf database.