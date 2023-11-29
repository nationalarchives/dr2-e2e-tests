from time import sleep
import boto3
import sys
import botocore.exceptions

ecs_client = boto3.client("ecs", region_name="eu-west-2")
ec2_client = boto3.client("ec2", region_name="eu-west-2")
logs_client = boto3.client("logs", region_name="eu-west-2")
logs = []
environment = sys.argv[1]

def check_exit_code(task_describe_response):
    container = task_describe_response["tasks"][0]["containers"][0]
    if "exitCode" in container:
        return container["exitCode"]


def get_task_status(task_exit_code, task_arns):
    if task_exit_code != 0 and task_exit_code is not None:
        raise RuntimeError(f"Exit code {task_exit_code} for task")
    elif task_exit_code == 0:
        return
    else:
        sleep(5)
        task_id = task_arns[0].split("/")[-1]
        log_to_console(task_id)
        next_describe_response = ecs_client.describe_tasks(tasks=task_arns)
        next_exit_code = check_exit_code(next_describe_response)
        get_task_status(next_exit_code, task_arns)


def log_to_console(task_id):
    log_stream_name = f"ecs/e2e-tests/{task_id}"
    log_group = "/aws/ecs/e2e-tests"
    try:
        logs_response = logs_client.get_log_events(logGroupName=log_group, logStreamName=log_stream_name)
        existing_timestamps = [log["timestamp"] for log in logs]
        new_events = [event for event in logs_response["events"] if event["timestamp"] not in existing_timestamps]
        logs.extend(new_events)
        for event in new_events:
            print(event["message"])
    except botocore.exceptions.ClientError:
        return


def run_tests():
    subnets_response = ec2_client.describe_subnets(
        Filters=[{'Name': 'tag:Name', 'Values': [f'{environment}-vpc-private-subnet-eu-west-2a']}]
    )
    security_groups_response = ec2_client.describe_security_groups(
        Filters=[{'Name': 'tag:Name', 'Values': [f'{environment}-outbound-https']}]
    )
    group_ids = [sec_group["GroupId"] for sec_group in security_groups_response["SecurityGroups"]]
    subnet_ids = [subnet["SubnetId"] for subnet in subnets_response["Subnets"]]
    response = ecs_client.run_task(
        launchType='FARGATE',
        networkConfiguration={
            'awsvpcConfiguration': {
                'subnets': subnet_ids,
                'securityGroups': group_ids
            }
        },
        taskDefinition='e2e-tests'
    )
    task_arns = [task["taskArn"] for task in response["tasks"]]
    describe_response = ecs_client.describe_tasks(tasks=task_arns, include=["TAGS"])
    exit_code = check_exit_code(describe_response)
    get_task_status(exit_code, task_arns)

run_tests()
