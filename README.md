# DR2 End to end tests

These are tests of the DR2 ingest process. They only include tests cases that will be successfully ingested into
Preservica.
Test cases which will fail mid-process will be dealt with in unit tests on the relevant lambdas.

## Structure

The tests use the `GivenWhenThen` trait from ScalaTest. This allows us to write BDD style tests but allows us to run
them easily in parallel.

## Running the tests

These tests are packaged into a docker image which is run in ECS. 
This allows us to access Preservica from inside the VPC. The task is triggered by a GitHub action defined
in [run.yml](./.github/workflows/run.yml)

## Environment variables

| Variable Name          | Description                                       |
|------------------------|---------------------------------------------------|
| ACCOUNT_ID             | Used to construct the SQS queue URL               |
| PRESERVICA_SECRET_NAME | The secret name containing Preservica credentials |
| ENVIRONMENT            | The environment the tests are running in          |
| PRESERVICA_API_URL     | The URL of the Preservica API                     |

## Running locally
You will need to be able to access the Preservica instance locally so if you need to run these locally, you'll need to run them against the sandbox Preservica instance.
