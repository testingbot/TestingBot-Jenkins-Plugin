# Jenkins TestingBot Plugin
==============================

This Jenkins plugin integrates TestingBot.com features inside Jenkins.

## Features

* Setup and teardown TestingBot Tunnel for testing internal websites, dev or staging environments. 
* Embed TestingBot Reports in your Jenkins job results; see screenshots/video of each tests from inside Jenkins.

## Prerequisites

* Minimum supported Jenkins version is 1.609.2.

## Embedded TestingBot Reports

The plugin will parse test results files in the post-build step to associate test results with TestingBot jobs.
The plugin will parse both `stdout` and `stderr`, looking for lines that have this format:

As part of the post-build activities, the Sauce plugin will parse the test result files in an attempt to associate test results with Sauce jobs. It does this by identifying lines in the stdout or stderr that have this format:

`TestingBotSessionID=<sessionId>`

The `sessionId` can be obtained from the `RemoteWebDriver` instance of Selenium.

## Building the Plugin

To build the plugin, use:

`mvn package`

## Reporting Issues

Please [file a new issue](https://github.com/testingbot/TestingBot-Jenkins-Plugin/issues).