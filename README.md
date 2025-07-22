<!--
SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>

SPDX-License-Identifier: CC0-1.0
-->

# Big Data Platform

[![REUSE Compliance](https://github.com/noi-techpark/bdp-core/actions/workflows/reuse.yml/badge.svg)](https://github.com/noi-techpark/odh-docs/wiki/REUSE#badges)
[![CI](https://github.com/noi-techpark/bdp-core/actions/workflows/main.yml/badge.svg)](https://github.com/noi-techpark/bdp-core/actions/workflows/main.yml)

The Big Data Platform is part of the [Open Data Hub](http://opendatahub.com/)
project. It collects and exposes data sets of various domains.

This platform collects heterogeneous data of different sources and different
domains, does elaborations on it and serves the raw and elaborated data through
a REST interface.

For a detailed introduction, see our [Big Data Platform
Introduction](https://opendatahub.readthedocs.io/en/latest/intro.html).

----

**Table of Contents**

- [Big Data Platform](#big-data-platform)
  - [Inbound API (writer)](#inbound-api-writer)
    - [Getting started with Docker](#getting-started-with-docker)
    - [Getting started natively](#getting-started-natively)
    - [Authentication](#authentication)
    - [DAL](#dal)
      - [Configuration of the database connection](#configuration-of-the-database-connection)
      - [Entities](#entities)
    - [DTO](#dto)
      - [StationDto](#stationdto)
      - [DataTypeDto](#datatypedto)
      - [SimpleRecordDto](#simplerecorddto)
    - [client](#client)
  - [Flight rules](#flight-rules)
    - [I want to generate a new schema dump out of Hibernate's Entity classes](#i-want-to-generate-a-new-schema-dump-out-of-hibernates-entity-classes)
    - [I want to update license headers of each source file](#i-want-to-update-license-headers-of-each-source-file)
    - [I want to see details of this project as HTML page](#i-want-to-see-details-of-this-project-as-html-page)
    - [I want to use client in my Java Maven project](#i-want-to-use-client-in-my-java-maven-project)
    - [I want to publish a new client sdk on our maven repository](#i-want-to-publish-a-new-client-sdk-on-our-maven-repository)
      - [Automatically via Github Actions](#automatically-via-github-actions)
      - [Manually from your machine](#manually-from-your-machine)
    - [I want to get started with a new data-collector](#i-want-to-get-started-with-a-new-data-collector)
  - [Information](#information)
    - [Support](#support)
    - [Contributing](#contributing)
    - [Documentation](#documentation)
    - [License](#license)
    - [REUSE](#reuse)

----

## Inbound API (writer)

The core of the platform contains the business logic of an **INBOUND** API which
handles connections to the database and provides an API for data collectors (see
[writer](#writer)), in form of a REST interface and a Java SDK (see
[client](#client)).

Finally, [dto](#dto) which is a library containinig all *Data Transfer Objects*
used by the `writer` and `client` to exchange data in a standardized
format.

The **OUTBOUND** API is called
[Ninja](https://github.com/noi-techpark/opendatahub-timeseries-api).

The writer is a REST API, which takes JSON DTOs, deserializes and validates them
and finally stores them in the database. Additionally, it sets stations to
active/inactive according to their presence inside the provided data. The writer
itself implements the methods to write data and is therefore the endpoint for
all data collectors. It uses the persistence-unit of the
[DAL](/writer/src/main/java/com/opendatahub/timeseries/bdp/writer/dal) which has full permissions on the
database.

The full API description can be found inside
[JsonController.java](writer/src/main/java/com/opendatahub/timeseries/bdp/writer/writer/JsonController.java).

### Getting started with Docker

If you want to run the application using [Docker](https://www.docker.com/), the
environment is already set up with all dependencies for you. This is the
recommended way to test and develop data collectors for this writer API. You
only have to install [Docker](https://www.docker.com/) and [Docker
Compose](https://docs.docker.com/compose/) and follow the instructions below.

In the root folder of this repository:

1) Copy `.env.example` to `.env`
2) Run `docker-compose up -d`
3) You can follow logs with `docker-compose logs -f`

Now you have a Postgres instance running on port 5555 and the API on port 8999.

Lets test Postgres first:

1) Login to the DB

    a) with Docker, do:
     ```
     $ docker-compose exec db bash
     bash-5.1# psql -U bdp bdp
     ```
    b) natively, do:
     ```
     PGPASSWORD=password psql -h localhost -p 5555 -U bdp bdp
     ```
2) Test the installation as follows:
```
bdp=# set search_path to intimev2;
bdp=# \dt

                  List of relations
  Schema  |           Name           | Type  | Owner
----------+--------------------------+-------+-------
 intimev2 | edge                     | table | bdp
 intimev2 | event                    | table | bdp
 intimev2 | flyway_schema_history    | table | bdp
 intimev2 | location                 | table | bdp
 intimev2 | measurement              | table | bdp
 intimev2 | measurementhistory       | table | bdp
 intimev2 | measurementjson          | table | bdp
 intimev2 | measurementjsonhistory   | table | bdp
 intimev2 | measurementstring        | table | bdp
 intimev2 | measurementstringhistory | table | bdp
 intimev2 | metadata                 | table | bdp
 intimev2 | provenance               | table | bdp
 intimev2 | station                  | table | bdp
 intimev2 | type                     | table | bdp
 intimev2 | type_metadata            | table | bdp
(15 rows)
```
... if you see a similar output as above, then you are set!

Please use the `curl` commands inside the chapter
[Authentication](#authentication) to test the writer API.

### Getting started natively

If you do not want to use docker, you can also start this application manually.
You need Java 17 and maven, and a Postgres DB. Postgresql can eventually also be
started with our [Docker setup](#getting-started-with-docker). Just call
`docker-compose up -d db`. It runs on port 5555. Alternatively, install and
start your own Postgresql instance.

The database, schema and the privileged user must already exist, if that is not
the case create them:

```sql
-- These values are already set inside the application.properties file, so you do
-- not need to configure anything except the port if you keep them like this!
create database bdp;
create user 'bdp' with login password 'password';
create schema if not exists 'intimev2';
grant all on schema intimev2 to bdp;
```

To start the writer, do the following:
1) Open `writer/src/main/resources/application.properties` and configure it,
   this step can be omitted if you use our dockerized Postgresql. For your own
   Postgres, just alter the port to 5432 and make sure you use the same names as
   shown above. Otherwise, configure also those parameters...
2) Start the Java application with `mvn spring-boot:run`

The application itself will create tables and other database objects for you. If
you prefer to do that manually, set `spring.flyway.enabled=false` and execute
the SQL files inside `writer/src/main/resources/db/migration` yourself. Replace
`${default_schema}` with your default schema, most probably `intimev2`.

Please use the `curl` commands inside the chapter
[Authentication](#authentication) to test the writer API.

### Authentication
We use Keycloak to authenticate. That service provides an `access_token` that
can be used to send POST requests to the writer. See the [Open Data Hub
Authentication / Quick
Howto](https://opendatahub.readthedocs.io/en/latest/guidelines/authentication.html#quick-howto)
for further details.

```sh
curl -X POST -L "https://auth.opendatahub.testingmachine.eu/auth/realms/noi/protocol/openid-connect/token" \
    --header 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'grant_type=client_credentials' \
    --data-urlencode 'client_id=odh-mobility-datacollector-development' \
    --data-urlencode 'client_secret=7bd46f8f-c296-416d-a13d-dc81e68d0830'
```

With this call you get an `access_token` that can then be used as follows in all
writer API methods. Here just an example to get all stations:

```sh
curl -X GET "http://localhost:8999/json/stations" \
    --header 'Content-Type: application/json' \
    --header 'Authorization: bearer your-access-token'
```

You should get an empty JSON list as result.

Write an email to `help@opendatahub.com`, if you want to get the `client_secret`
and an Open Data Hub OAuth2 account for a non-development setup.

### DAL

DAL is the *Data Access Layer* which communicates with the DB underneath used by
the writer modules. The communication is handled through the ORM Hibernate and
its spatial component for geometries. The whole module got developed using
PostgreSQL as database and Postgis as an extension.

Connection pooling is handled by
[HikariCP](https://github.com/brettwooldridge/HikariCP) for high speed
connections to the DB.

In some cases geometry transformations and elaborations were needed to be
executed on application level and therefore [Geotools](http://www.geotools.org/)
was added as dependency.

#### Configuration of the database connection

To configure the DAL module to communicate with your database you need to
provide configuration and credentials inside
> `writer/src/main/resources/application.properties`

Default can be found at:

> `writer/src/main/resources/META-INF/persistence.xml`

Please note, values inside the `application.properties` file, overwrite values
inside `persistence.xml`.

We use a [schema-generator](infrastructure/utils/schema-generator/README.md) to
generate the schema for the database. After that you can manually check what the
difference between that schema and the old one is and provide a new flyway
script inside `writer/src/main/resources/db/migration`.

Hibernate, our object-relational-mapping (ORM) framework, handles the schema
validation only (for security reasons). Usually, we set the value
`hibernate.hbm2ddl.auto = validate` during development and
`hibernate.hbm2ddl.auto = none` at runtime for performance reasons on startup.

#### Entities

This chapter describes the most important DAL entities:
- `station`
- `datatype`
- `record`
- `edge`

**Station**

The `station` represents the origin of the data which needs an identifier, a
name, a coordinate and a so called `stationtype`. It also should contain the
origin of the data, the current *active* state (if actively used or not) and if
it has a parent station, used to model a hierarchical station structure. For all
remaining data, which enriches the station, we created a field *metadata*. It
can hold any kind of meta information in form of a JSON object. To understand
the functionality and the main job of this entity check the source code
[Station.java](dal/src/main/java/com/opendatahub/timeseries/bdp/dal/Station.java).

*Example*:
>A station can be of stationtype `MeteoStation`, has an identifier `89935GW` and
a position `latitude":46.24339407235059,"longitude":11.199431152658656`. It can
have additional information like address, municipality, opening times etc.,
which would be modelled as meta data entry.


**DataType**

The `data type` represents the typology of the data in form of an unique name
and a unit. Description and metric of measurements can also be provided.

*Example*:
>A `temperature` can have a unit `°C` and can be an `average` value of the last
>300 seconds (called `period`).

**Record**

A `record` represents a single measurement containing a `value`, a `timestamp` ,
a `data-type`, a `station`, and a `provenance`. Provenance indicates which data
collector in which version collected the data. It is needed to implement
traceability between collectors and inserted data, to identify data for
cleansing or bug fixes.

*Example*:
> We measure on `Fri Dec 16 2016 10:47:33` a data type `temperature` (see data
> type example) of `20.4` for a meteo station called `89935GW` (see station
> example).

**Edge**

An edge represents the spatial geometry between two stations. We model this
internally as a station triple: `origin, edge, destination`, because currently
only stations can be exposed through our API. We add a line-geometry to that
triple to describe the entity geographically. Hereby, `origin` and `destination`
are two stations of any type that represent two points on the map. The `edge` is
also a station of type `LinkStation`, that has no coordinates. It is the
description of the edge.

*Example*:
> A street between two stations, where the measured data could be how many cars
> passed it.

If you need more information about specific entities or classes, try to use the
javadoc or source code inside [DAL](writer/src/main/java/com/opendatahub/timeseries/bdp/dal).

### DTO
Data transfer objects (DTOs) are used to define the structure of the data
exchange. They are used between data provider and data persister (`writer`).
They consist of fields which are all primitives and easily serializable. The
[DTO](dto) module is a java library contained in all modules of the big data
platform, simply because it defines the communication structure in between.

The following chapters describe the most used DTOs.

#### StationDto
Describes a place where measurements get collected. It is the origin of the
data. We define the structure inside
[StationDto.java](dto/src/main/java/com/opendatahub/timeseries/bdp/dto/dto/StationDto.java).

#### DataTypeDto
Describes a specific type of data. We define the structure inside
[DataTypeDto.java](dto/src/main/java/com/opendatahub/timeseries/bdp/dto/dto/DataTypeDto.java)

#### SimpleRecordDto
Describes the measured value. We define the structure inside
[SimpleRecordDto.java](dto/src/main/java/com/opendatahub/timeseries/bdp/dto/dto/SimpleRecordDto.java)

### client
The client contains the API through which components can communicate with
the BDP writer. Just include the `client` [maven
dependency](#i-want-to-use-client-in-my-java-maven-project)
in your project and use the existing [JSON client
implementation](client/src/main/java/com/opendatahub/timeseries/bdp/json/JSONPusher.java).

The API contains several methods. We describe the most important methods here,
for the rest see
[JSONPusher.java](client/src/main/java/com/opendatahub/timeseries/bdp/client/json/JSONPusher.java)
implementation.

**`Object getDateOfLastRecord(String stationCode,String dataType,Integer period)`**

This method is required to get the date of the last valid record


**`Object syncStations(List<StationDto> data)`**

This method is used to create, update, and deactivate stations of a specific
typology; `data` must be a list of StationDto's

**`Object syncDataTypes(List<DataTypeDto> data)`**

This method is used to create and update(and therefore upsert) data types;
`data` must be a list of DataTypeDto

**`Object pushData(DataMapDto<? extends RecordDtoImpl> dto)`**

This is the place, where you place all the data you want to pass to the writer.
The data in here gets saved in form of a tree.

Each branch can have multiple child branches, but can also have data itself,
which means it can have indefinite depth. Right now, by our internal conventions
we store everything on the second level, like this:

```
+- Station
   |
   +- DataType
      |
      `-Data
```

As value you can put a list of
[SimpleRecordDto.java](dto/src/main/java/com/opendatahub/timeseries/bdp/dto/dto/SimpleRecordDto.java),
which contains all the data points with a specific station and  a specific type.
Each point is represented as timestamp and value. To better understand the
structure, see the
[DataMapDto.java](dto/src/main/java/com/opendatahub/timeseries/bdp/dto/dto/DataMapDto.java)
source.

## Flight rules

### I want to generate a new schema dump out of Hibernate's Entity classes

See [README.md](infrastructure/utils/schema-generator/README.md) inside
`/infrastructure/utils/schema-generator`.

### I want to update license headers of each source file
To update license headers in each source code file run `mvn license:format`. To
configure the header template edit `LICENSE/templates/` files, and set the
correct attributes inside each `pom.xml`. See the plugin
[license-maven-plugin](http://code.mycila.com/license-maven-plugin/) homepage
for details. Use the `quicklicense.sh` script to update all source code license
headers at once.

### I want to see details of this project as HTML page
Run `mvn site` to create a HTML page with all details of this project. Results
can be found under `<project>/target/site/`, entrypoint is as usual
`index.html`.

### I want to use client in my Java Maven project
Include the following snippet in your `pom.xml` file:
```
	<repositories>
		<repository>
			<id>maven-repo.opendatahub.com</id>
			<url>https://maven-repo.opendatahub.com/snapshot</url>
		</repository>
	</repositories>
```

Include the dependency `client` for data collectors:
```
<dependency>
  <groupId>com.opendatahub.timeseries.bdp</groupId>
  <artifactId>client</artifactId>
  <version>7.3.0</version>
</dependency>
```

You can also use a version-range, like `[7.3.0,8.0.0)`. Find the latest version
in our [release channel](https://github.com/noi-techpark/bdp-core/releases) on
GitHub.

### I want to publish a new client sdk on our maven repository

This chapter is for the NOI team only. It describes how to publish a new
client manually or via the Github Action workflow on our maven repo.
Either as "release" or "snapshot" version...

#### Automatically via Github Actions

*SNAPSHOT RELEASES*: If you push code to the `main` branch, which changes
either `dto` or `client` the Github Action workflow deploys a new snapshot
version of those libraries. The version is then the latest version tag on the
`prod` branch and a `-SNAPSHOT` postfix. For example, if the version tag is
`v7.4.0`, then the new snapshot version string is `7.4.0-SNAPSHOT` (the initial
`v` will be removed).

*PRODUCTION RELEASES*: Push your code to the `prod` branch and tag it with a
semantic versioning tag prefixed by `v`. As you might notice in the past we had version tags without that prefix, but the new Github Action workflow requires it, so in future please always put it like this. For example, `v7.5.0`.

#### Manually from your machine

Create a file `~/.m2/settings.xml`, and copy/paste the following code:
```xml
<settings>
    <servers>
        <server>
            <id>maven-repo.opendatahub.com-release</id>
            <username>your-remote-repos-username</username>
            <password>your-remote-repos-password</password>
        </server>
        <server>
            <id>maven-repo.opendatahub.com-snapshot</id>
            <username>your-remote-repos-username</username>
            <password>your-remote-repos-password</password>
        </server>
    </servers>
</settings>
```

Replace `your-remote-repos-username` and `your-remote-repos-password` with your
Maven repo credentials. We have a group on our AWS/IAM called
`s3-odh-maven-repo` that gives permissions to push to the maven repo. Assign
that role to your user eventually, or search for `s3-odh-maven-repo` on our
password server for credentials.

Update all `pom.xml` files with the correct version. Here an example to create a
snapshot release with version `8.0.1` (do not put a `v` prefix):
`./infrastructure/utils/quickrelease.sh snapshot 8.0.1`

Use `./infrastructure/utils/quickrelease.sh release 8.0.1` for a production release.

Call `mvn --projects dto --projects client --also-make clean install deploy`

### I want to get started with a new data-collector

Refer to the [Contributing chapter] and our [HelloWorld Example Data Collector] inside
https://github.com/noi-techpark/bdp-commons to start a new data collector.

[Contributing chapter]: https://github.com/noi-techpark/bdp-commons/blob/main/README.md#contributing
[HelloWorld Example Project]: https://github.com/noi-techpark/bdp-commons/tree/main/data-collectors/helloworld

## Information

### Support

For support, please contact [help@opendatahub.com](mailto:help@opendatahub.com).

### Contributing

If you'd like to contribute, please follow our [Getting
Started](https://github.com/noi-techpark/odh-docs/wiki/Contributor-Guidelines:-Getting-started)
instructions.

### Documentation

More documentation can be found at
[https://docs.opendatahub.com](https://docs.opendatahub.com).

### License

The code in this project is licensed under the GNU GENERAL PUBLIC LICENSE
Version 3 license. See the [LICENSE](LICENSE) file for more information.

### REUSE

This project is [REUSE](https://reuse.software) compliant, more information about the usage of REUSE in NOI Techpark repositories can be found [here](https://github.com/noi-techpark/odh-docs/wiki/Guidelines-for-developers-and-licenses#guidelines-for-contributors-and-new-developers).

Since the CI for this project checks for REUSE compliance you might find it useful to use a pre-commit hook checking for REUSE compliance locally. The [pre-commit-config](.pre-commit-config.yaml) file in the repository root is already configured to check for REUSE compliance with help of the [pre-commit](https://pre-commit.com) tool.

Install the tool by running:
```bash
pip install pre-commit
```
Then install the pre-commit hook via the config file by running:
```bash
pre-commit install
```
