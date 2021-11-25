# windup-api Project

- [REST endpoints](#rest-endpoints)
- [Run on Minikube](#run-on-minikube)
- [Try with the sample page](#try-with-the-sample-page)
  * [Try with the sample configuration](#try-with-the-sample-configuration)
  * [Try with the custom configuration](#try-with-the-custom-configuration)
  * [Try with Swagger UI](#try-with-swagger-ui)
- [Try with Tackle](#try-with-tackle)
- [Customizations](#customizations)
  * [Max size for uploaded applications](#max-size-for-uploaded-applications)

<small><i><a href='http://ecotrust-canada.github.io/markdown-toc/'>Table of contents generated with markdown-toc</a></i></small>

The Windup API component is a Kubernetes application meant to provide access to information created from Windup's rules execution during an analysis.  
The project and the code are at an ***early development stage*** so keep in mind the API endpoints are not stable yet but in tech preview.  
That's why we would like to listen to your feedback opening a new [Issue](https://github.com/windup/windup-api/issues) to tell us what went well and what can be improved.  
Please check frequently for updates and new features additions.  

Features available:
* trigger the analysis of a compiled application posting the application archive (jar, war, ear)
* retrieve the hints identified for the application
* retrieve the hints for all the applications analyzed
* get analysis status updates while analysis is running

Features to be added: check the [Issues](https://github.com/windup/windup-api/issues) for enhancements already planned and feel free to add your request for new features if it's not there yet.  

## REST endpoints

All the available endpoints are described with OpenAPI specifications in the [openapi.yaml](src/main/resources/META-INF/openapi.yaml) file.  
It can be analyzed, for example, with the online [Swagger Editor](https://editor.swagger.io/?url=https://raw.githubusercontent.com/windup/windup-api/main/src/main/resources/META-INF/openapi.yaml)  
A Swagger UI is also embedded in the Windup API and accessible from the provided sample page as described below in the [Try with Swagger UI](#try-with-swagger-ui) paragraph.  

## Run on Minikube
Install a Minikube instance following instructions from.  
Once Minikube is up and running, create the `windup` namespace executing
```shell
kubectl create namespace windup
```
and then deploy the Windup API using
```shell
kubectl apply -n windup -f https://raw.githubusercontent.com/windup/windup-api/main/minikube.yaml
```
Now all the images for running the containers are going to be pulled, so it might take some time (:coffee: ?).  
You can know when the Windup API is available, waiting for the `windup-api` deployment to meet the `Available` condition execution:
```shell
kubectl wait -n windup --for condition=Available deployment windup-api --timeout=-1s
```
As soon as the `windup-api` deployment will be available, the following message will be displayed:
```shell
deployment.apps/windup-api condition met
```
Now you can start testing the Windup API leveraging the provided sample page executing
```shell
minikube service -n windup api
```
that will open your default browser directly with the provided sample page.  

If you want to remove all the resources create, you can run:
```shell
kubectl delete -n windup -f https://raw.githubusercontent.com/windup/windup-api/main/minikube.yaml
kubectl delete namespace windup
```

## Try with the sample page

The provided sample page (see below screenshot) is meant to ease the initial testing with the Windup API.  

![Windup Sample Page](docs/images/windup-sample-page.png?raw=true "Windup Sample Page")

### Try with the sample configuration

The sample page provides a sample analysis configuration immediately usable clicking on the button `Request Analysis with sample configuration`.  
The sample configuration will analyze the [jee-example-app-1.0.0.ear](./src/main/resources/META-INF/resources/samples/jee-example-app-1.0.0.ear) towards the Red Hat JBoss EAP 7, Quarkus, Cloud-readiness and Red Hat Runtimes targets.  

### Try with the custom configuration

The `Custom Configuration` form in the sample page let the user trigger an analysis with the desired values for the input parameters.  

### Try with Swagger UI

In the sample page, it's also available, on the right side, the link `OpenAPI with Swagger UI` that will take you to the Swagger UI with preloaded the OpenAPI file for the available endpoints.  
This is useful for quickly execute some tests of the endpoints.

## Try with Tackle

An early (i.e. proof of concept) integration between [Tackle](https://github.com/konveyor/tackle), the tools that support the modernization and migration of applications to Kubernetes from [Konveyor](https://www.konveyor.io/) community, and Windup API is available.  
A kubernetes manifest for deploying the integrated versions of Tackle and Windup API is provided.  
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
kubectl apply -n windup -f https://raw.githubusercontent.com/windup/windup-api/main/windup-api-with-tackle.yaml
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
The credentials to login into the Tackle UI are username `tackle` and password `password`.  
Then, once a new application has been created, the `Analyze` command -for triggering an analysis with Windup API- is available in the right menu in the application row (see next screenshot).  

![Tackle analyze application](docs/images/tackle-select-analyze.png?raw=true "Tackle analyze application")  

## Customizations

### Max size for uploaded applications

Windup API default configuration allows the upload of applications up to 100 MB.  
This size can be changed applying a different value to the `QUARKUS_HTTP_LIMITS_MAX_BODY_SIZE` environment variable executing:
```shell
kubectl set -n windup env deployment windup-api QUARKUS_HTTP_LIMITS_MAX_BODY_SIZE="<new_value>"
```
