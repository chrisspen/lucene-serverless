"""
Lambda function to report realtime EFS file sizes.
"""
import os

def lambda_handler(event, context):
    total_size = 0
    start_path = '/mnt/data'  # Your Lucene data directory

    for dirpath, dirnames, filenames in os.walk(start_path):
        for f in filenames:
            fp = os.path.join(dirpath, f)
            total_size += os.path.getsize(fp)

    return {
        'statusCode': 200,
        'body': f'Total size of Lucene index: {total_size / (1024 * 1024)} MB'
    }
