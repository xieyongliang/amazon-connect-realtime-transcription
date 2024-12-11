from boto3.dynamodb.conditions import Key
import traceback
import helper

websocket_connection_table = 'ws_connection'
ddbh = helper.ddb_helper({'table_name': websocket_connection_table})

def lambda_handler(event, context):
    print(event)
    
    connection_id = event['requestContext']['connectionId']
    
    try:    
        params = {}
        params['connection_id'] = connection_id
        ddbh.put_item(params)
        
        return {
            'statusCode': 200
        }
    
    except Exception as e:
        traceback.print_exc()
        print(e)
    
        return {
            'statusCode': 400,
            'body': str(e)
        }
