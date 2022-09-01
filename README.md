# windup-api Project

- [Introduction](#introduction)
  * [Summary](#summary)
  * [Features](#features)
- [Technical Considerations](#technical-considerations)
  * [Requirements](#requirements)
  * [REST endpoints](#rest-endpoints)
  * [Configuration parameters](#configuration-parameters)
    + [Max size for uploaded applications](#max-size-for-uploaded-applications)
- [Deployment Guide](#deployment-guide)
  * [Minikube](#minikube)
    + [API with sample page](#api-with-sample-page)
    + [API with Tackle](#api-with-tackle)
  * [OCP](#ocp)
    + [API with sample page](#api-with-sample-page-1)
    + [API with Tackle](#api-with-tackle-1)
- [Usage Guide](#usage-guide)
  * [API with Swagger](#api-with-swagger)
  * [API with sample page](#api-with-sample-page-2)
  * [API with Tackle](#api-with-tackle-2)

# Introduction

## Summary

The Tackle Windup API component is a Kubernetes application meant to provide access to information created from Windup's rules execution during an analysis.  
The project and the code are at an ***early development stage*** so keep in mind the API endpoints are not stable yet but in tech preview.  
That's why we would like to listen to your feedback opening a new [Issue](https://github.com/konveyor/tackle-windup-api/issues) to tell us what went well and what can be improved.  
Please check frequently for updates and new features additions.  

## Features

Features available:
* trigger the analysis of a compiled application posting the application archive (jar, war, ear)
* retrieve the hints identified for the application
* retrieve the hints for all the applications analyzed
* get analysis status updates while analysis is running

Features to be added: check the [Issues](https://github.com/konveyor/tackle-windup-api/issues) for enhancements already planned and feel free to add your request for new features if it's not there yet.  

# Technical Considerations

## Requirements

For deploying the "API with sample page" the requirements are:

* 10 GB file system for Persistent Volumes that supports access mode `ReadWriteMany`
* 10 GB file system for Persistent Volumes that supports access mode `ReadWriteOnce`

For deploying the "API with Tackle" the requirements are:

* 10 GB file system for Persistent Volumes that supports access mode `ReadWriteMany`
* 14 GB file system for Persistent Volumes that supports access mode `ReadWriteOnce`

## REST endpoints

All the available endpoints are described with OpenAPI specifications in the [openapi.yaml](src/main/resources/META-INF/openapi.yaml) file.  
It can be analyzed, for example, with the online [Swagger Editor](https://editor.swagger.io/?url=https://raw.githubusercontent.com/konveyor/tackle-windup-api/main/src/main/resources/META-INF/openapi.yaml)  
A Swagger UI is also embedded in the Tackle Windup API and accessible from the provided sample page as described below in the [API with Swagger](#api-with-swagger) paragraph.  

## Configuration parameters

### Max size for uploaded applications

Tackle Windup API default configuration allows the upload of applications up to 100 MB.  
This size can be changed applying a different value to the `QUARKUS_HTTP_LIMITS_MAX_BODY_SIZE` environment variable executing:
```shell
kubectl set -n windup env deployment windup-api QUARKUS_HTTP_LIMITS_MAX_BODY_SIZE="<new_value>"
```
where `<new_value>` follows the [Quarkus MemorySize format](https://quarkus.io/guides/http-reference#memory-size-note-anchor).  

# Deployment Guide

## Minikube
Install and start a Minikube instance following instructions from [minikube start](https://minikube.sigs.k8s.io/docs/start/).  

### API with sample page

Once Minikube is up and running, create the `windup` namespace executing
```shell
kubectl create namespace windup
```
and then deploy the Tackle Windup API using
```shell
kubectl apply -n windup -f https://raw.githubusercontent.com/konveyor/tackle-windup-api/main/minikube.yaml
```
Now all the images for running the containers are going to be pulled, so it might take some time (:coffee: ?).  
You can know when the Tackle Windup API is available, waiting for the `windup-api` deployment to meet the `Available` condition execution:
```shell
kubectl wait -n windup --for condition=Available deployment windup-api --timeout=-1s
```
As soon as the `windup-api` deployment will be available, the following message will be displayed:
```shell
deployment.apps/windup-api condition met
```
Now you can start testing the Tackle Windup API leveraging the provided sample page executing
```shell
minikube service -n windup api
```
that will open your default browser directly with the provided sample page.  
Now you can move to the [Usage Guide - API with Swagger](#api-with-swagger) and [Usage Guide - API with sample page](#api-with-sample-page-2) to get details on how to use them.  

If later you want to remove all the resources create, you can run:  
```shell
kubectl delete -n windup -f https://raw.githubusercontent.com/konveyor/tackle-windup-api/main/minikube.yaml
kubectl delete namespace windup
```

### API with Tackle

An early (i.e. proof of concept) integration between [Tackle](https://github.com/konveyor/tackle), the tools that support the modernization and migration of applications to Kubernetes from [Konveyor](https://www.konveyor.io/) community, and Tackle Windup API is available.  
A kubernetes manifest for deploying the integrated versions of Tackle and Tackle Windup API is provided.  
Before deploying it, enable the `ingress` Minikube addon with  
```shell
minikube addons enable ingress
```
Then deploy creating, if not already done, the `windup` namespace running  
```shell
kubectl create namespace windup
```
and then executing  
```shell
kubectl apply -n windup -f https://raw.githubusercontent.com/konveyor/tackle-windup-api/main/windup-api-with-tackle.yaml
```
You can check if the deployments have been successfully done executing and waiting for the next command to finish  
```shell
kubectl -n windup wait deployment --all --for condition=Available --timeout=-1s
```
The expected outcome would be like (the order of the entries can be different):  
```shell
deployment.apps/application-inventory-postgres condition met
deployment.apps/artemis condition met
deployment.apps/controls-postgres condition met
deployment.apps/keycloak condition met
deployment.apps/keycloak-postgres condition met
deployment.apps/pathfinder-postgres condition met
deployment.apps/tackle-application-inventory condition met
deployment.apps/tackle-controls condition met
deployment.apps/tackle-pathfinder condition met
deployment.apps/tackle-ui condition met
deployment.apps/windup-api condition met
deployment.apps/windup-executor condition met
```
Once deployed, the Tackle UI can be opened retrieving the Minikube IP executing  
```shell
minikube ip
```
and open a browser at the provided IP value (e.g. `192.168.49.2`).  
Now you can move to the [Usage Guide - API with Tackle](#api-with-tackle-2) to get details on how to use the deployed applications.  

## OCP

You need to have an instance of Openshift Container Platform (OCP).

### API with sample page

On your OCP instance, create a project `windup` executing
```shell
oc new-project windup
```

then upload API's template to your project executing
```shell
oc create -n windup -f https://raw.githubusercontent.com/konveyor/tackle-windup-api/main/openshift/windup-api.yaml
```

finally, instantiate the template executing:
```shell
oc process windup-api -n windup | oc create -f -
```

Now all the images for running the containers are going to be pulled, so it might take some time.

You can know when the Tackle Windup API is available, waiting for the `windup-api` deployment to meet the `Available` condition execution:
```shell
oc wait -n windup --for condition=Available deployment windup-api --timeout=-1s
```

As soon as the `windup-api` deployment will be available, the following message will be displayed:
```shell
deployment.apps/windup-api condition met
```

Now you can start testing the Tackle Windup API opening, in your browser, the Route url provided by OCP.  
The route's URL is the value provided in the `HOST/PORT` column in the output of the command:
```shell
oc get routes -n windup
```

Now you can move to the [Usage Guide - API with Swagger](#api-with-swagger) and [Usage Guide - API with sample page](#api-with-sample-page-2) to get details on how to use them.  

If later you want to remove all the resources created, you can run:  
```shell
oc delete -n windup -f https://raw.githubusercontent.com/konveyor/tackle-windup-api/main/openshift/windup-api.yaml
oc delete project windup
```

### API with Tackle

An early (i.e. proof of concept) integration between [Tackle](https://github.com/konveyor/tackle), the tools that support the modernization and migration of applications to Kubernetes from [Konveyor](https://www.konveyor.io/) community, and Tackle Windup API is available.

A kubernetes manifest for deploying the integrated versions of Tackle and Tackle Windup API is provided.  

On your OCP instance, create a project `windup` executing
```shell
oc new-project windup
```

then upload API's template to your project executing
```shell
oc create -n windup -f https://raw.githubusercontent.com/konveyor/tackle-windup-api/main/openshift/windup-api-with-tackle.yaml
```

finally, instantiate the template executing:
```shell
oc process windup-api-with-tackle -n windup | oc create -f -
```

You can check if the deployments have been successfully done executing and waiting for the next command to finish  
```shell
oc -n windup wait deployment --all --for condition=Available --timeout=-1s
```

The expected outcome would be like (the order of the entries can be different):  
```shell
deployment.apps/application-inventory-postgres condition met
deployment.apps/artemis condition met
deployment.apps/controls-postgres condition met
deployment.apps/keycloak condition met
deployment.apps/keycloak-postgres condition met
deployment.apps/pathfinder-postgres condition met
deployment.apps/tackle-application-inventory condition met
deployment.apps/tackle-controls condition met
deployment.apps/tackle-pathfinder condition met
deployment.apps/tackle-ui condition met
deployment.apps/windup-api condition met
deployment.apps/windup-executor condition met
```

Once deployed, the Tackle UI can be opened through the Route provided by OCP.  
The route's URL is the value provided in the `HOST/PORT` column in the output of the command:

```shell
oc get routes -n windup
```

Open a browser pointing to the Route url provided by the previous command.

Now you can move to the [Usage Guide - API with Tackle](#api-with-tackle-2) to get details on how to use the deployed applications.  


# Usage Guide

## API with Swagger

In the sample page (ref. screenshot below), on the right side, the link `OpenAPI with Swagger UI` that will take you to the Swagger UI with preloaded the OpenAPI file for the available endpoints.  
This is useful for quickly execute some tests of the endpoints.

## API with sample page

The provided sample page (see below screenshot) is meant to ease the initial testing with the Tackle Windup API.  

![Windup Sample Page](docs/images/windup-sample-page.png?raw=true "Windup Sample Page")

The sample page has two options for testing the API:

* _Sample Configuration_: this form is immediately usable clicking on the button `Request Analysis with sample configuration`.  
The sample configuration will analyze the [jee-example-app-1.0.0.ear](./src/main/resources/META-INF/resources/samples/jee-example-app-1.0.0.ear) towards the Red Hat JBoss EAP 7, Quarkus, Cloud-readiness and Red Hat Runtimes targets.  
* _Custom Configuration_: this form in the sample page let the user trigger an analysis with the desired values for the input parameters.  

## API with Tackle

The credentials to login into the Tackle UI are username `tackle` and password `password`.  
Then, once a new application has been created, the `Analyze` command -for triggering an analysis with Tackle Windup API- is available in the right menu in the application row (see next screenshot).  

![Tackle analyze application](docs/images/tackle-select-analyze.png?raw=true "Tackle analyze application")  


## Code of Conduct
Refer to Konveyor's Code of Conduct [here](https://github.com/konveyor/community/blob/main/CODE_OF_CONDUCT.md).
