import argparse
import asyncio
import datetime
import json
import os
import logging
import threading
import time
import sys
from enum import Enum
from collections import deque
from flask import Flask, render_template, request, jsonify
from flask_socketio import SocketIO

import quickfix as fix
from fix_client import FixClient, Side, OrderType, TimeInForce

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler("logs/app.log"),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger("FixApp")

# Initialize Flask app
app = Flask(__name__,
            static_folder='static',
            template_folder='templates')
app.config['SECRET_KEY'] = 'fix-trading-app'
socketio = SocketIO(app, cors_allowed_origins="*")

# Initialize FIX client
fix_client = None
client_lock = threading.Lock()
message_buffer = deque(maxlen=1000)  # Store last 1000 messages
is_connected = False

# Create a thread-specific event loop for the FIX client
fix_event_loop = asyncio.new_event_loop()
fix_thread = None

def run_fix_event_loop():
    """Run the FIX client event loop in a separate thread"""
    asyncio.set_event_loop(fix_event_loop)
    fix_event_loop.run_forever()

# Start the event loop thread
fix_thread = threading.Thread(target=run_fix_event_loop, daemon=True)
fix_thread.start()

# Function to run a coroutine in the FIX event loop
def run_in_fix_loop(coro):
    """Run a coroutine in the FIX event loop and return the result"""
    future = asyncio.run_coroutine_threadsafe(coro, fix_event_loop)
    return future.result()

def message_to_dict(message):
    """Convert FIX message to dictionary for easier processing in UI"""
    if isinstance(message, str):
        try:
            msg = fix.Message(message)
        except:
            return {"raw": message}
    else:
        msg = message

    # Always ensure we have a string representation
    raw_msg = msg.toString().replace('\x01', '|')
    result = {"raw": raw_msg}

    # Extract message type
    header = msg.getHeader()
    if header.isSetField(fix.MsgType()):
        msg_type = fix.MsgType()
        header.getField(msg_type)
        result["msgType"] = msg_type.getValue()

    # Extract common fields
    fields_to_extract = [
        (fix.ClOrdID, "clOrdID"),
        (fix.OrderID, "orderID"),
        (fix.Symbol, "symbol"),
        (fix.Side, "side"),
        (fix.OrdType, "ordType"),
        (fix.OrderQty, "orderQty"),
        (fix.Price, "price"),
        (fix.OrdStatus, "ordStatus"),
        (fix.ExecID, "execID"),
        (fix.TimeInForce, "timeInForce"),
        (fix.LastShares, "lastQty"),
        (fix.LastPx, "lastPx"),
        (fix.LeavesQty, "leavesQty"),
        (fix.CumQty, "cumQty"),
        (fix.AvgPx, "avgPx"),
        (fix.ExecType, "execType"),
        (fix.Text, "text"),
        (fix.TransactTime, "transactTime"),
    ]

    for field_class, field_name in fields_to_extract:
        if msg.isSetField(field_class()):
            field = field_class()
            msg.getField(field)
            # Make sure to convert to a basic Python type
            result[field_name] = str(field.getValue())

    # Add execution specific fields
    if result.get("msgType") == "8":  # Execution Report
        # Extract additional execution fields
        if msg.isSetField(fix.LastMkt()):
            last_mkt = fix.LastMkt()
            msg.getField(last_mkt)
            result["lastMkt"] = str(last_mkt.getValue())

        if msg.isSetField(fix.ExecTransType()):
            exec_trans_type = fix.ExecTransType()
            msg.getField(exec_trans_type)
            result["execTransType"] = str(exec_trans_type.getValue())

        # Map execution type to human-readable format
        if "execType" in result:
            exec_types = {
                "0": "NEW",
                "1": "PARTIAL_FILL",
                "2": "FILL",
                "3": "DONE_FOR_DAY",
                "4": "CANCELED",
                "5": "REPLACED",
                "6": "PENDING_CANCEL",
                "7": "STOPPED",
                "8": "REJECTED",
                "9": "SUSPENDED",
                "A": "PENDING_NEW",
                "B": "CALCULATED",
                "C": "EXPIRED",
                "D": "ACCEPTED",
                "E": "PENDING_REPLACE"
            }
            result["execTypeText"] = exec_types.get(result["execType"], result["execType"])

        # Map order status to human-readable format
        if "ordStatus" in result:
            order_statuses = {
                "0": "NEW",
                "1": "PARTIALLY_FILLED",
                "2": "FILLED",
                "3": "DONE_FOR_DAY",
                "4": "CANCELED",
                "5": "REPLACED",
                "6": "PENDING_CANCEL",
                "7": "STOPPED",
                "8": "REJECTED",
                "9": "SUSPENDED",
                "A": "PENDING_NEW",
                "B": "CALCULATED",
                "C": "EXPIRED",
                "D": "ACCEPTED",
                "E": "PENDING_REPLACE"
            }
            result["ordStatusText"] = order_statuses.get(result["ordStatus"], result["ordStatus"])

    if msg.isSetField(fix.TransactTime()):
        # Try multiple approaches to get the transaction time
        try:
            # First, check if we can get it from the string representation
            field_parts = raw_msg.split('|')
            for part in field_parts:
                if part.startswith('60='):  # Tag 60 is TransactTime
                    time_str = part[3:]
                    # Parse FIX date format YYYYMMDD-HH:MM:SS or YYYYMMDD-HH:MM:SS.sss
                    if len(time_str) >= 17:  # Minimum length for YYYYMMDD-HH:MM:SS
                        # Convert to more readable format
                        try:
                            if '.' in time_str:
                                dt = datetime.datetime.strptime(time_str, "%Y%m%d-%H:%M:%S.%f")
                            else:
                                dt = datetime.datetime.strptime(time_str, "%Y%m%d-%H:%M:%S")
                            result["transactTime"] = dt.strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
                            break
                        except:
                            result["transactTime"] = time_str
                            break
                    else:
                        result["transactTime"] = time_str
                        break

            # If we couldn't extract it from the string, use the field object
            if "transactTime" not in result:
                transact_time = fix.TransactTime()
                msg.getField(transact_time)
                result["transactTime"] = str(transact_time)
        except Exception as e:
            logger.error(f"Error extracting transaction time: {e}")
            result["transactTime"] = time.strftime("%Y-%m-%d %H:%M:%S")


    # Map side to human-readable format
    if "side" in result:
        sides = {
            "1": "BUY",
            "2": "SELL"
        }
        result["sideText"] = sides.get(result["side"], result["side"])

    # Map order type to human-readable format
    if "ordType" in result:
        order_types = {
            "1": "MARKET",
            "2": "LIMIT"
        }
        result["ordTypeText"] = order_types.get(result["ordType"], result["ordType"])

    # Map time in force to human-readable format
    if "timeInForce" in result:
        tifs = {
            "0": "DAY",
            "1": "GTC",
            "3": "IOC",
            "4": "FOK"
        }
        result["timeInForceText"] = tifs.get(result["timeInForce"], result["timeInForce"])

    # Determine direction (inbound/outbound)
    if header.isSetField(fix.SenderCompID()) and header.isSetField(fix.TargetCompID()):
        sender = fix.SenderCompID()
        target = fix.TargetCompID()
        header.getField(sender)
        header.getField(target)

        # Get our sender ID from config
        sender_id = None
        try:
            with open("client.cfg", "r") as f:
                config_lines = f.readlines()
                for line in config_lines:
                    if line.strip().startswith("SenderCompID="):
                        sender_id = line.strip().split("=")[1]
                        break
        except:
            sender_id = None

        if sender_id and sender.getValue() == sender_id:
            result["direction"] = "outbound"
        else:
            result["direction"] = "inbound"

    # Add timestamp
    result["timestamp"] = time.strftime("%Y-%m-%d %H:%M:%S")

    return result

def add_message_to_buffer(message, direction=None):
    """Add message to buffer and broadcast to connected clients"""
    try:
        msg_dict = message_to_dict(message)
        if direction and "direction" not in msg_dict:
            msg_dict["direction"] = direction

        with client_lock:
            message_buffer.append(msg_dict)

        # Make sure all values are JSON serializable
        for key in list(msg_dict.keys()):
            if not isinstance(msg_dict[key], (str, int, float, bool, list, dict, type(None))):
                msg_dict[key] = str(msg_dict[key])

        socketio.emit('fix_message', msg_dict)
    except Exception as e:
        logger.error(f"Error processing message for UI: {e}", exc_info=True)
        # Try a simpler approach as fallback
        try:
            if isinstance(message, str):
                simple_msg = {"raw": message, "direction": direction, "timestamp": time.strftime("%Y-%m-%d %H:%M:%S")}
            else:
                simple_msg = {"raw": message.toString().replace('\x01', '|'), "direction": direction, "timestamp": time.strftime("%Y-%m-%d %H:%M:%S")}
            socketio.emit('fix_message', simple_msg)
        except:
            logger.error("Failed to send even simplified message", exc_info=True)

async def start_fix_client():
    """Start the FIX client"""
    global fix_client, is_connected

    if fix_client is not None:
        logger.info("FIX client already running")
        return {"success": False, "error": "Client already running"}

    try:
        fix_client = FixClient("client.cfg")

        # Override client methods to capture messages
        original_from_app = fix_client.fromApp
        original_to_app = fix_client.toApp
        original_from_admin = fix_client.fromAdmin
        original_to_admin = fix_client.toAdmin



        def fromApp_override(message, sessionID):
            try:
                msg_copy = fix.Message(message.toString())
                add_message_to_buffer(msg_copy, "inbound")
            except Exception as e:
                logger.error(f"Error in fromApp_override: {e}", exc_info=True)
            return original_from_app(message, sessionID)

        def toApp_override(message, sessionID):
            try:
                msg_copy = fix.Message(message.toString())
                add_message_to_buffer(msg_copy, "outbound")
            except Exception as e:
                logger.error(f"Error in toApp_override: {e}", exc_info=True)
            return original_to_app(message, sessionID)

        def fromAdmin_override(message, sessionID):
            try:
                msg_copy = fix.Message(message.toString())
                add_message_to_buffer(msg_copy, "inbound")
            except Exception as e:
                logger.error(f"Error in fromAdmin_override: {e}", exc_info=True)
            return original_from_admin(message, sessionID)

        def toAdmin_override(message, sessionID):
            try:
                msg_copy = fix.Message(message.toString())
                add_message_to_buffer(msg_copy, "outbound")
            except Exception as e:
                logger.error(f"Error in toAdmin_override: {e}", exc_info=True)
            return original_to_admin(message, sessionID)

        fix_client.fromApp = fromApp_override
        fix_client.toApp = toApp_override

        # Start the client
        connected = await fix_client.start()

        if connected:
            is_connected = True
            socketio.emit('connection_status', {'connected': True})
            return {"success": True}
        else:
            fix_client = None
            return {"success": False, "error": "Failed to connect"}

    except Exception as e:
        logger.error(f"Error starting FIX client: {e}", exc_info=True)
        if fix_client:
            await fix_client.stop()
            fix_client = None
        return {"success": False, "error": str(e)}

async def stop_fix_client():
    """Stop the FIX client"""
    global fix_client, is_connected

    if fix_client is None:
        return {"success": False, "error": "Client not running"}

    try:
        await fix_client.stop()
        fix_client = None
        is_connected = False
        socketio.emit('connection_status', {'connected': False})
        return {"success": True}
    except Exception as e:
        logger.error(f"Error stopping FIX client: {e}", exc_info=True)
        return {"success": False, "error": str(e)}

@app.route('/')
def index():
    """Render the main application page"""
    return render_template('main.html')

@app.route('/api/connect', methods=['POST'])
def connect():
    """Connect to FIX counterparty"""
    global is_connected

    if is_connected:
        return jsonify({"success": False, "error": "Already connected"})

    data = request.json

    # If SenderCompID or TargetCompID provided, update config
    if 'senderCompID' in data or 'targetCompID' in data:
        try:
            # Load the config file
            with open("client.cfg", "r") as f:
                config_content = f.read()

            # Parse the config
            lines = config_content.split('\n')
            updated_lines = []

            in_session_section = False
            for line in lines:
                # Track when we're in the SESSION section
                if line.strip() == '[SESSION]':
                    in_session_section = True
                    updated_lines.append(line)
                    continue

                # Check for the next section, which would end the SESSION section
                if in_session_section and line.strip().startswith('['):
                    in_session_section = False

                # If we're in SESSION section, look for SenderCompID and TargetCompID
                if in_session_section:
                    if line.strip().startswith('SenderCompID=') and 'senderCompID' in data and data['senderCompID']:
                        updated_lines.append(f"SenderCompID={data['senderCompID']}")
                    elif line.strip().startswith('TargetCompID=') and 'targetCompID' in data and data['targetCompID']:
                        updated_lines.append(f"TargetCompID={data['targetCompID']}")
                    else:
                        updated_lines.append(line)
                else:
                    updated_lines.append(line)

            # Write the updated config back to the file
            with open("client.cfg", "w") as f:
                f.write('\n'.join(updated_lines))

            logger.info(f"Updated config with SenderCompID={data.get('senderCompID')} and TargetCompID={data.get('targetCompID')}")

        except Exception as e:
            logger.error(f"Error updating config: {e}")
            return jsonify({"success": False, "error": f"Error updating config: {e}"})

    # Start FIX client in background
    try:
        result = run_in_fix_loop(start_fix_client())
        return jsonify(result)
    except Exception as e:
        logger.error(f"Error connecting: {e}", exc_info=True)
        return jsonify({"success": False, "error": str(e)})


@app.route('/api/disconnect', methods=['POST'])
def disconnect():
    """Disconnect from FIX counterparty"""
    global is_connected

    if not is_connected:
        return jsonify({"success": False, "error": "Not connected"})

    # Stop FIX client in background
    try:
        result = run_in_fix_loop(stop_fix_client())
        return jsonify(result)
    except Exception as e:
        logger.error(f"Error disconnecting: {e}", exc_info=True)
        return jsonify({"success": False, "error": str(e)})

@app.route('/api/status', methods=['GET'])
def status():
    """Get connection status"""
    return jsonify({"connected": is_connected})

@app.route('/api/messages', methods=['GET'])
def get_messages():
    """Get message history"""
    with client_lock:
        messages = list(message_buffer)
    return jsonify({"messages": messages})

@app.route('/api/send_order', methods=['POST'])
def send_order():
    """Send a new order"""
    global fix_client, is_connected

    if not is_connected or fix_client is None:
        return jsonify({"success": False, "error": "Not connected"})

    data = request.json
    logger.info(f"Sending order: {data}")

    try:
        # Extract order parameters
        symbol = data.get('symbol', '')
        side_str = data.get('side', '')
        order_type_str = data.get('orderType', '')
        quantity = float(data.get('quantity', 0))
        price = float(data.get('price', 0)) if data.get('price') else None
        time_in_force_str = data.get('timeInForce', '')

        # Convert string values to enums
        if side_str == 'BUY':
            side = Side.BUY
        elif side_str == 'SELL':
            side = Side.SELL
        else:
            side = side_str

        if order_type_str == 'MARKET':
            order_type = OrderType.MARKET
        elif order_type_str == 'LIMIT':
            order_type = OrderType.LIMIT
        else:
            order_type = order_type_str

        if time_in_force_str == 'DAY':
            time_in_force = TimeInForce.DAY
        elif time_in_force_str == 'GTC':
            time_in_force = TimeInForce.GTC
        elif time_in_force_str == 'IOC':
            time_in_force = TimeInForce.IOC
        elif time_in_force_str == 'FOK':
            time_in_force = TimeInForce.FOK
        else:
            time_in_force = time_in_force_str

        # Create a new loop for this request
        loop = asyncio.new_event_loop()
        try:
            exec_report = loop.run_until_complete(fix_client.send_new_order(
                symbol=symbol,
                side=side,
                order_type=order_type,
                quantity=quantity,
                price=price,
                time_in_force=time_in_force
            ))
        finally:
            loop.close()

        # The message should already be captured by the message handlers
        # Just return success whether we got an exec report or not
        return jsonify({"success": True})

    except Exception as e:
        logger.error(f"Error sending order: {e}", exc_info=True)
        return jsonify({"success": False, "error": str(e)})

@app.route('/api/cancel_order', methods=['POST'])
def cancel_order():
    """Cancel an existing order"""
    global fix_client, is_connected

    if not is_connected or fix_client is None:
        return jsonify({"success": False, "error": "Not connected"})

    data = request.json

    try:
        # Extract cancel parameters
        cl_ord_id = data.get('clOrdID', '')
        symbol = data.get('symbol', '')
        side_str = data.get('side', '')
        quantity = float(data.get('quantity', 0))

        # Convert string values to enums
        side = Side[side_str] if side_str in Side.__members__ else side_str

        # Send cancel in background
        loop = asyncio.new_event_loop()
        result = loop.run_until_complete(fix_client.cancel_order(
            cl_ord_id=cl_ord_id,
            symbol=symbol,
            side=side,
            quantity=quantity
        ))
        loop.close()

        if result:
            return jsonify({"success": True, "report": message_to_dict(result)})
        else:
            return jsonify({"success": False, "error": "No cancel response received"})

    except Exception as e:
        logger.error(f"Error canceling order: {e}", exc_info=True)
        return jsonify({"success": False, "error": str(e)})

@socketio.on('connect')
def handle_connect():
    """Handle client connection to WebSocket"""
    socketio.emit('connection_status', {'connected': is_connected})

if __name__ == '__main__':
    # Create logs directory if it doesn't exist
    parser = argparse.ArgumentParser()
    parser.add_argument('--port', type=int, default=5000, help='Port to run the server on')
    args = parser.parse_args()

    os.makedirs('logs', exist_ok=True)

    # Set higher recursion limit for asyncio
    sys.setrecursionlimit(2000)

    # Run the app
    socketio.run(app, host='0.0.0.0', port=args.port, debug=True, allow_unsafe_werkzeug=True)