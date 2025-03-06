# Cerberus Executor image (Work in progress)

[Cerberus](http://www.cerberus-testing.org/) is an user-friendly automated testing framework.

This Docker image run a Cerberus executor instance.

## Tags

Hereafter list of available tags:

Tag     | Description                        | Source
--------|------------------------------------|-------------------------------


## Prerequisites



## How to run this image

### Run the image

This image can simply be run by using the following command:

    docker run -d -P cerberustesting/cerberus-executor:latest

Note the use of the `-d` and `-P` arguments to let image be run as deamon and open ports outside container which is the common use.

### Image Environment variables



### Image Exposed ports

Hereafter list of exposed ports when image is running (inherited from the [tomcat image](https://hub.docker.com/_/tomcat/)).

Exposed port            | Definition
------------------------|---------------------------------------------------------
`8092`                  | access port


## Docker compose
An example of docker-compose file is available [here](https://github.com/cerberustesting/cerberus-source/tree/master/docker/compositions/cerberus-tomcat-mysql)


## License

Cerberus Copyright (C) 2013 - 2017 cerberustesting

This file is part of Cerberus.

Cerberus is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Cerberus is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Cerberus.  If not, see <http://www.gnu.org/licenses/>.



