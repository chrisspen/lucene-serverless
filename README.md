# Lucene Serverless

This project demonstrates a proof-of-concept serverless full-text search solution built with Apache Lucene and Quarkus framework.

✔️ No servers

✔️ No fixed costs

✔️ Low (250-300ms) cold starts

⚠️ Better deletion policy is required. Right now old segment files are not deleted as a simple workaround to handle concurrent reads and writes

ℹ️ Cost can be controlled via several factors:

* EFS' [Elastic](https://docs.aws.amazon.com/efs/latest/ug/performance.html#throughput-modes) throughput mode cost more $$, but scales better up and down
* Lambda's [Provisioned Concurrency](https://docs.aws.amazon.com/lambda/latest/dg/provisioned-concurrency.html) provides faster, more consistent first request times but costs more $$
* Lambda memory is kept ~256mb, tune based on index size and observed speeds. Note: vCPU is allocated proportional to memory.

Please note that the project is not ready for production since I haven't tested it under a prolonged load and to be honest interfaces need to be nicer.

Read the blog post about it [here](https://medium.com/@arsenyyankovski/serverless-full-text-search-with-aws-lambda-and-efs-cf24e1b6fe3b)

## Prerequisites
- [Serverless framework >= 1.56.1](https://serverless.com/framework/docs/getting-started/)
- AWS account

## Build it

    mvn clean compile
    mvn package

## Run it
1. Define environment variables for the org, region, vpc id and subnets in the `serverless.yml` file:

    export LUCENE_SERVERLESS_ORG=<org>
    export LUCENE_SERVERLESS_VPC_ID=<vpc-id>
    export LUCENE_SERVERLESS_SUBNET_ID1=<subnet-id1>
    export LUCENE_SERVERLESS_SUBNET_ID2=<subnet-id2>

2. Deploy the stack
   `npx sls deploy`

3. Don't forget to remove it if you're not planning to use it
   `npx sls remove`

### Index a document

URL: `https://<api-id>.execute-api.<region>.amazonaws.com/dev/index`

HTTP method: POST

Example request body:

```json
{
  "indexName": "books",
  "documents": [
    {
      "name": "The Foundation",
      "author": "Isaac Asimov"
    }
  ]
}
```

### Query documents

URL: `https://<api-id>.execute-api.<region>.amazonaws.com/dev/query`

HTTP method: POST

Example request body:

```json
{
   "indexName": "books",
   "query": "author:isaac"
}
```

Example response body:

```json
{
  "totalDocuments": "1",
  "documents": [
    {
      "author": "Isaac Asimov",
      "name": "The Foundation"
    }
  ]
}
```

## Build native image
`./mvnw clean package`
