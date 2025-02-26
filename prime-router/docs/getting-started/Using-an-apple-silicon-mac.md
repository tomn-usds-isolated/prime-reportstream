# Using Apple Silicon Macs for Development

## Problems

In general, running Intel Docker images on Apple Silicon is a hit-or-miss proposition even with Apple's Rosetta technology. 
See the known Docker's documentation for known limitations: https://docs.docker.com/desktop/mac/apple-silicon/#known-issues

At the time of the writing of this note, Microsoft's Azure Function Docker image is only `amd64` compatible, and  not compatible with Apple Silicon processors. 
Microsoft has not announced plans to fix this problem. 

Many of our local developer tools are set up to run in Docker containers. 
Looking at our `docker-compose.yml` file, the `web_receiver`, `prime_dev`, and `settings` services do not work on Apple Silicon.
Likewise, the `builder` service in our `docker-compose.build.yml` does not work as well. 

## Workaround

Fortunately, ReportStream can run directly on your Apple Silicon computer, outside of Docker containers. 
The approach outlined in this note uses `gradle` to set up your environment and to run your code directly. 
This approach has the benefit of reducing your build-debug cycle time and is detailed in the [Running Faster](faster-development.md) document. 

### Step 1 - Read the getting started instructions

Read the [Getting Started](../getting_started.md) instructions as background information about various components 
needed to work in ReportStream. This document may have new information not found in this document. 

### Step 2 - Install dev tools

Installing the recommend tools including these for this note.

* [git](getting-started/install-git.md) 
* [Docker Desktop](getting-started/install-docker.md) Install Docker Desktop directly. 
* [OpenJDK](getting-started/install-openjdk.md) Install OpenJDK 11 using Brew. 
* [Azure Functions Core Tools](getting-started/install-afct.md) Install the v3 of Azure Functions.
* [Gradle](getting-started/install-gradle.md) Install Gradle using Brew. 

An IDE of your choice. Both VS Code and JetBrain's IntelliJ have ARM64 versions. 

### Step 3 - Run `cleanslate.sh`

The `cleanslate.sh` script does the base work needed to start developing for ReportStream. 
It only needs to be run once.  
This script runs on Apple processors, but it skips a few steps.  
We will need to do these missing steps by hand. 

```bash
# build the project
./cleanslate.sh --verbose
# ...

# Check that a Postgres instance is running
docker ps
# CONTAINER ID   IMAGE         COMMAND                  CREATED          STATUS          PORTS                    NAMES
# 2962fb214203   postgres:11   "docker-entrypoint.s…"   57 minutes ago   Up 57 minutes   0.0.0.0:5432->5432/tcp   prime-router_postgresql_1
```

### Step 4 - Run support services

ReportStream depends on set of services to be up before running main Azure service. These services include:

* Azurite - a simulator of Azure storage
* Vault - a secret store
* SFTP - A SFTP server
* MockServer - A web server mocking tool for Redox 

You can run these services using the `docker-compose` tool. 

```bash
docker-compose up sftp redox azurite ftps vault 
```

Look over the log in your terminal session and check for any errors. 
If you find any, read the [Things that might go wrong](#things-that-might-go-wrong) section. 

You can take down these services using a "ctrl-c" keyboard combination or the `docker-compose down` command. 
For now, leave these services running and open up a new terminal session. 

> Note: If you do not want to devote a whole terminal session the logs of these services. 
> You can run them in detached mode `docker-compose up --detach sftp redox azurite ftps vault` and then
> attach to the containers when you want to examine the logs. 

### Step 5 - Run ReportStream locally
With the dependent services running and a freshly built JAR created by `cleanslate.sh`, we can run ReportStream locally. 
We use Gradle to launch ReportStream, because Gradle will set up the environment variables that ReportStream needs. 

```bash
gradle quickrun
```

ReportStream should continue to run after launching. A `ctrl-c` will kill the running ReportStream instance. 
For now, keep ReportStream running, open a new terminal session.

### Step 6 - Seed the Postgres DB and Vault
To run tests, the Postgres DB and the credential vault need to be seeded with values. 
We will need to have ReportStream running for these steps to work (see previous steps). 
Again, we will use a Gradle task to do these steps.

```bash
gradle primeCLI --args "create-credential --type=UserPass --persist=DEFAULT-SFTP --user foo --pass pass"
gradle reloadSettings
```

### Step 7 - Run tests
You should be able to run tests now to confirm that everything is working. 

```bash
# Smoke test checks that most of the system is working
gradle testSmoke
```
Another test that is run is the integration test. 
```bash
gradle testIntegration
```

### Step 6 - Build Frontend

You should be able to build the frontend locally per the [ReadMe](../frontend/readme.md) of the frontend project. 
Be sure to the have the ReportStream function running per previous steps. 

```bash
cd ./frontend/
npm ci
npm run build

# static site root built in `frontend/dist`
ls ./dist
```

### Step 7 - Test Frontend

Navigate to `http://localhost:8090/index.html`. 
You should be able to login and exercise the UI. 

## Next Steps

Now that you have builds and tests running, here are a few pointers to common recipes that developers use. 

### Build and run the ReportStream functions

After a code update, a fast way to build and run the code again is
```bash
gradle package fatjar
gradle quickrun
```

### Debugging
`gradle quickrun` will open a debug port on your locally running ReportStream instance. 
Connect your debugger remotely to port 5005. 
For profiling use the JMX port 9090.

### Using gradle
`gradle tasks` will list all the tasks that Gradle can do for you. 

### Start and stop dependent services

Instead of using a `docker-compose up sftp redox azurite ftps vault` to bring up dependent services, you can use a script.
```bash
./devenv-infrastructure.sh up
```
This script runs in a detached mode so the rather noisy logs of these services are not in your face. 
The same script can bring down dependent services.
```bash
./devenv-infrastructure.sh down
```
To see what containers are currently running: 
```bash
docker ps
```
To examine the logs of dependent service that is currently running.
```bash
docker logs NAME --follow
```
where NAME is replaced by the container name. 
For example, `prime-router_azurite_1` is the name of the Azurite container.

### Format your code
The Kotlin code in the project follows the KTLint style guide. 
There is a git hook that checks for conformance to this style guide.
To reformat your new code to be in compliance: 
```bash
gradle ktlintFormat 
```

### Add environment variables to your shell profile

Both the ReportStream Azure Functions and ReportStream CLI use environment variables extensively. 
This note uses `gradle` tasks to set up these environment variables for you. 
You can, however, set up these variables directly in your shell profile script. 
In this way, you can run the './prime' CLI and functions directly.  
Here's a list of environment variables that are used at the time of writing this note. 

```bash
CREDENTIAL_STORAGE_METHOD=HASHICORP_VAULT
VAULT_TOKEN=<get from .vault/env/env.local>
VAULT_API_ADDR=http://localhost:8200
AzureWebJobsStorage="DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=<find online>;BlobEndpoint=http://localhost:10000/devstoreaccount1;QueueEndpoint=http://localhost:10001/devstoreaccount1;"
PartnerStorage="DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=<find online>;BlobEndpoint=http://localhost:10000/devstoreaccount1;QueueEndpoint=http://localhost:10001/devstoreaccount1;"
POSTGRES_USER=prime
POSTGRES_PASSWORD=changeIT!
POSTGRES_URL=jdbc:postgresql://localhost:5432/prime_data_hub
PRIME_ENVIRONMENT=local
OKTA_baseUrl=hhs-prime.okta.com
OKTA_clientId=0oa6fm8j4G1xfrthd4h6
OKTA_redirect=http://localhost:7071/api/download
JAVA_HOME=$(/usr/libexec/java_home)
```

## Things That Might Go Wrong

You may run into problems. Here are few of the common ones.

### FTPS container

If you get an error running `docker-compose up sftp redox azurite ftps vault` like *bind source path does not exist: .../build/ftps*.
This is likely because a `clean` command has removed the build `build/ftps` directory. You can add the directory by hand using `mkdir build/ftps` and the `docker-compose` will run.

### Azurite

Building while Azurite is running can cause a problem if the `clean` Gradle task is used, because the clean task will remove the `build` folder that Azurite uses. You can solve this by restarting Azurite.

### Vault files

A message like
```
Couldn't find env file: /Users/username/Projects/prime-reportstream/prime-router/.vault/env/.env.local
```
usually means that local vault files have been deleted.
Running `.cleanslate.sh` again will populate these files.

### Docker-compose warnings
A warning that is common is an orphan container warning.
```
WARNING: Found orphan containers (prime-router_postgresql_1) for this project.
```
This is a benign warning caused by running Postgres in different `docker-compose` script. 


