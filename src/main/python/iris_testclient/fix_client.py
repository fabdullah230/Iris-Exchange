import datetime

import quickfix as fix
import asyncio
import uuid
import sys
import threading
import time
import logging
from enum import Enum
from concurrent.futures import ThreadPoolExecutor

# Set up logging with efficient configuration
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[logging.StreamHandler()]
)
logger = logging.getLogger("FixClient")

class Side(Enum):
    BUY = '1'
    SELL = '2'

class OrderType(Enum):
    MARKET = '1'
    LIMIT = '2'

class TimeInForce(Enum):
    DAY = '0'
    GTC = '1'
    IOC = '3'
    FOK = '4'

import quickfix as fix
import datetime

class SafeTimeStamp:
    """
    Safe wrapper for UtcTimeStamp to prevent memory leaks
    """

    @staticmethod
    def get_current_time_object():
        """Get a quickfix UtcTimeStamp object for current time"""
        # Create a UTC timestamp that QuickFIX can use
        now = datetime.datetime.utcnow()
        return fix.UtcTimeStamp(
            now.hour,
            now.minute,
            now.second,
            now.microsecond // 1000,  # Convert to milliseconds
            now.day,
            now.month,
            now.year
        )

class FixClient(fix.Application):
    """
    Optimized FIX Protocol client for trading applications.
    Uses asyncio for event handling with improved performance.
    """

    def __init__(self, config_path):
        super().__init__()
        self.session_id = None
        self.config_path = config_path
        self.initiator = None
        self.running = False
        self.executor = ThreadPoolExecutor(max_workers=4)  # For parallel processing

        # Store the event loop to use for cross-thread communication
        self.loop = asyncio.get_event_loop()

        # Keep asyncio.Event for compatibility
        self.logon_event = asyncio.Event()
        self.execution_events = {}
        self.exec_reports = {}
        self.message_store = {}
        self.logger = logger

        # Thread-safe queue for events from other threads
        self.event_queue = asyncio.Queue()

        # Start a task to process the event queue
        self.event_task = self.loop.create_task(self._process_events())

        # Pre-allocate message buffer pool for better performance
        self.message_buffer_pool = []
        for _ in range(10):  # Pre-allocate 10 message buffers
            self.message_buffer_pool.append(fix.Message())

    async def _process_events(self):
        """Process events from other threads"""
        while True:
            try:
                event_type, data = await self.event_queue.get()
                if event_type == "logon":
                    self.logon_event.set()
                elif event_type == "logout":
                    self.logon_event.clear()
                elif event_type == "execution":
                    cl_ord_id, message = data
                    self.exec_reports[cl_ord_id] = message
                    if cl_ord_id in self.execution_events:
                        self.execution_events[cl_ord_id].set()
                self.event_queue.task_done()
            except asyncio.CancelledError:
                break
            except Exception as e:
                self.logger.error(f"Error processing event: {e}")

    def _get_buffered_message(self):
        """Get a message from the buffer pool or create a new one"""
        if self.message_buffer_pool:
            return self.message_buffer_pool.pop()
        return fix.Message()

    def _return_message_to_pool(self, message):
        """Return a message to the buffer pool"""
        if len(self.message_buffer_pool) < 20:  # Limit pool size
            message.clear()
            self.message_buffer_pool.append(message)

    def onCreate(self, sessionID):
        self.logger.info(f"Session created: {sessionID}")

    def onLogon(self, sessionID):
        self.session_id = sessionID
        self.logger.info(f"Logon successful: {sessionID}")

        # Queue the logon event instead of directly calling set()
        # This avoids the "no current event loop" error
        self.loop.call_soon_threadsafe(lambda: self.event_queue.put_nowait(("logon", None)))

    def onLogout(self, sessionID):
        self.logger.info(f"Logout: {sessionID}")
        self.session_id = None

        # Queue the logout event
        self.loop.call_soon_threadsafe(lambda: self.event_queue.put_nowait(("logout", None)))

    def toAdmin(self, message, sessionID):
        # Optimize field extraction - only get once
        header = message.getHeader()
        msgType = fix.MsgType()
        header.getField(msgType)

        if msgType.getValue() == fix.MsgType_Logon:
            # Set ResetSeqNumFlag only once
            message.setField(fix.ResetSeqNumFlag(True))
            self.logger.info("Setting ResetSeqNumFlag=Y for Logon message")

    def fromAdmin(self, message, sessionID):
        # Only extract fields when needed
        header = message.getHeader()
        msgType = fix.MsgType()
        header.getField(msgType)

        if msgType.getValue() == fix.MsgType_ResendRequest:
            beginSeqNo = fix.BeginSeqNo()
            endSeqNo = fix.EndSeqNo()
            message.getField(beginSeqNo)
            message.getField(endSeqNo)

            # Handle resend in a separate thread to avoid blocking
            self.executor.submit(
                self._handle_resend_request,
                sessionID,
                beginSeqNo.getValue(),
                endSeqNo.getValue()
            )

    def _handle_resend_request(self, sessionID, beginSeqNo, endSeqNo):
        """Optimized resend request handler"""
        seq_reset = self._get_buffered_message()
        header = seq_reset.getHeader()

        # Set required fields efficiently
        header.setField(fix.BeginString(sessionID.getBeginString()))
        header.setField(fix.MsgType(fix.MsgType_SequenceReset))
        header.setField(fix.SenderCompID(sessionID.getSenderCompID()))
        header.setField(fix.TargetCompID(sessionID.getTargetCompID()))

        # Set gap fill fields
        seq_reset.setField(fix.NewSeqNo(int(endSeqNo) + 1))
        seq_reset.setField(fix.GapFillFlag(True))

        # Send and log
        fix.Session.sendToTarget(seq_reset, sessionID)
        self._return_message_to_pool(seq_reset)
        self.logger.info(f"Sent SequenceReset-GapFill with NewSeqNo={int(endSeqNo)+1}")

    def toApp(self, message, sessionID):
        # More efficient logging approach
        if self.logger.isEnabledFor(logging.INFO):
            msgType = fix.MsgType()
            message.getHeader().getField(msgType)
            self.logger.info(f"Sending message: {msgType.getValue()}")

        # Only store messages if needed for resends
        header = message.getHeader()
        if header.isSetField(fix.MsgSeqNum()):
            seqNum = fix.MsgSeqNum()
            header.getField(seqNum)
            # Store as string to reduce memory usage
            self.message_store[seqNum.getValue()] = message.toString()

    def fromApp(self, message, sessionID):
        """Handle application messages"""
        try:
            # Extract message type
            header = message.getHeader()
            msgType = fix.MsgType()
            header.getField(msgType)
            msgTypeValue = msgType.getValue()

            # Log all messages
            self.logger.info(f"Received message: {msgTypeValue}")

            # Store a copy of the message
            message_copy = fix.Message(message.toString())

            # Process different message types
            if msgTypeValue == fix.MsgType_ExecutionReport:
                self._handle_execution_report(message)
            elif msgTypeValue == fix.MsgType_OrderCancelReject:
                self._handle_cancel_reject(message)
            else:
                # For other message types, create a generic identifier
                msg_id = f"MSG-{uuid.uuid4()}"[:16]

                # Queue the message through the event loop
                self.loop.call_soon_threadsafe(
                    lambda: self.event_queue.put_nowait(("execution", (msg_id, message_copy)))
                )
        except Exception as e:
            self.logger.error(f"Error processing message: {e}", exc_info=True)

    def _handle_execution_report(self, message):
        """Optimized execution report handler with thread-safe asyncio handling"""
        try:
            # Extract client order ID once
            if message.isSetField(fix.ClOrdID()):
                cl_ord_id_field = fix.ClOrdID()
                message.getField(cl_ord_id_field)
                cl_ord_id = cl_ord_id_field.getValue()

                # Extract status if available
                status = None
                if message.isSetField(fix.OrdStatus()):
                    ord_status = fix.OrdStatus()
                    message.getField(ord_status)
                    status = ord_status.getValue()
                    self.logger.info(f"Received execution report for order {cl_ord_id}, status: {status}")
                else:
                    self.logger.info(f"Received execution report for order {cl_ord_id}")

                # Use message copy to prevent issues with QuickFIX message lifecycle
                message_copy = fix.Message(message.toString())

                # Store the execution report directly
                self.exec_reports[cl_ord_id] = message_copy

                # Check if there's a waiting event and set it
                if cl_ord_id in self.execution_events:
                    try:
                        # Try using the event loop if it's available
                        if self.loop and not self.loop.is_closed():
                            self.loop.call_soon_threadsafe(
                                lambda: self.execution_events[cl_ord_id].set()
                            )
                        else:
                            # Fallback approach - manually set the event from this thread
                            # This is safe since Event.set() is thread-safe
                            self.execution_events[cl_ord_id].set()
                    except Exception as e:
                        self.logger.error(f"Error setting execution event: {e}")
            else:
                self.logger.warning("Received execution report without ClOrdID")
        except Exception as e:
            self.logger.error(f"Error processing message: {e}", exc_info=True)

    def _handle_cancel_reject(self, message):
        """Optimized cancel reject handler with thread-safe asyncio handling"""
        if not message.isSetField(fix.ClOrdID()):
            return

        cl_ord_id_field = fix.ClOrdID()
        message.getField(cl_ord_id_field)
        cl_ord_id = cl_ord_id_field.getValue()

        reason = "Unknown"
        if message.isSetField(fix.CxlRejReason()):
            reason_field = fix.CxlRejReason()
            message.getField(reason_field)
            reason = reason_field.getValue()

        self.logger.warning(f"Cancel rejected for order {cl_ord_id}, reason: {reason}")

        # Queue the reject event through the event loop
        message_copy = fix.Message(message.toString())
        self.loop.call_soon_threadsafe(
            lambda: self.event_queue.put_nowait(("execution", (cl_ord_id, message_copy)))
        )

    async def start(self):
        """Optimized client startup with asyncio"""
        try:
            # Create required QuickFIX components
            settings = fix.SessionSettings(self.config_path)
            store_factory = fix.FileStoreFactory(settings)
            log_factory = fix.ScreenLogFactory(settings)

            # Create and start initiator
            self.initiator = fix.SocketInitiator(self, store_factory, settings, log_factory)
            self.initiator.start()
            self.running = True

            # Start heartbeat thread
            self.hb_thread = threading.Thread(target=self._heartbeat_runner, daemon=True)
            self.hb_thread.start()

            # Wait for logon with better timeout
            try:
                await asyncio.wait_for(self.logon_event.wait(), timeout=5)
                self.logger.info("Logon successful, ready to send orders")
                return True
            except asyncio.TimeoutError:
                self.logger.error("Logon timeout")
                await self.stop()
                return False

        except Exception as e:
            self.logger.error(f"Error starting FIX client: {e}", exc_info=True)
            return False

    async def send_new_order(self, symbol, side, order_type, quantity, price=None, time_in_force=TimeInForce.DAY):
        """Optimized order sending with asyncio"""
        if not self.session_id:
            self.logger.error("Not logged in, cannot send order")
            return None

        # Generate shorter UUIDs for better performance
        cl_ord_id = str(uuid.uuid4())[:16]

        # Create event
        event = asyncio.Event()
        self.execution_events[cl_ord_id] = event

        try:
            # Create order message more efficiently
            order = self._create_new_order_message(
                cl_ord_id=cl_ord_id,
                symbol=symbol,
                side=side.value if isinstance(side, Side) else side,
                order_type=order_type.value if isinstance(order_type, OrderType) else order_type,
                quantity=quantity,
                price=price,
                time_in_force=time_in_force.value if isinstance(time_in_force, TimeInForce) else time_in_force
            )

            # Log minimal info
            self.logger.info(f"Sending new order: {cl_ord_id}, {symbol}, {side.name if isinstance(side, Side) else side}, {quantity}")

            # Send the order
            if not fix.Session.sendToTarget(order, self.session_id):
                self.logger.error(f"Failed to send order {cl_ord_id}")
                del self.execution_events[cl_ord_id]
                return None

            # Wait for execution report with a shorter timeout
            try:
                await asyncio.wait_for(event.wait(), timeout=5)  # Reduced timeout
                return self.exec_reports.get(cl_ord_id)
            except asyncio.TimeoutError:
                self.logger.warning(f"Timeout waiting for execution report for order {cl_ord_id}")
                return None

        except Exception as e:
            self.logger.error(f"Error sending order: {e}")
            return None
        finally:
            # Clean up
            self.execution_events.pop(cl_ord_id, None)  # More efficient than del with try/except

    def _create_new_order_message(self, cl_ord_id, symbol, side, order_type, quantity, price=None, time_in_force='0'):
        """Optimized message creation"""
        # Get a message from the buffer pool
        message = self._get_buffered_message()
        header = message.getHeader()

        # Set message type
        header.setField(fix.MsgType(fix.MsgType_NewOrderSingle))

        # Required fields - set in optimal order for protocol
        message.setField(fix.ClOrdID(cl_ord_id))
        message.setField(fix.HandlInst('1'))
        message.setField(fix.Symbol(symbol))
        message.setField(fix.Side(side))

        # Use the empty constructor which sets current time automatically
        message.setField(fix.TransactTime())

        message.setField(fix.OrdType(order_type))
        message.setField(fix.OrderQty(float(quantity)))
        message.setField(fix.TimeInForce(time_in_force))

        # Only set price for limit orders
        if order_type == '2' and price is not None:
            message.setField(fix.Price(float(price)))

        return message

    async def cancel_order(self, cl_ord_id, symbol, side, quantity):
        """Optimized order cancellation with asyncio"""
        if not self.session_id:
            self.logger.error("Not logged in, cannot cancel order")
            return None

        # Shorter cancel ID
        cancel_cl_ord_id = f"C-{str(uuid.uuid4())[:8]}"

        # Create event
        event = asyncio.Event()
        self.execution_events[cancel_cl_ord_id] = event

        try:
            # Create a cancel message efficiently
            cancel = self._get_buffered_message()
            header = cancel.getHeader()
            header.setField(fix.MsgType(fix.MsgType_OrderCancelRequest))

            # Set fields in optimal order
            cancel.setField(fix.OrigClOrdID(cl_ord_id))
            cancel.setField(fix.ClOrdID(cancel_cl_ord_id))
            cancel.setField(fix.Symbol(symbol))
            cancel.setField(fix.Side(side.value if isinstance(side, Side) else side))
            cancel.setField(fix.TransactTime())  # Current time
            cancel.setField(fix.OrderQty(float(quantity)))

            self.logger.info(f"Sending cancel for order: {cl_ord_id}")

            # Send the cancel
            if not fix.Session.sendToTarget(cancel, self.session_id):
                self.logger.error(f"Failed to send cancel for order {cl_ord_id}")
                del self.execution_events[cancel_cl_ord_id]
                self._return_message_to_pool(cancel)
                return None

            # Return message to pool
            self._return_message_to_pool(cancel)

            # Wait for response
            try:
                await asyncio.wait_for(event.wait(), timeout=5)  # Reduced timeout
                return self.exec_reports.get(cancel_cl_ord_id)
            except asyncio.TimeoutError:
                self.logger.warning(f"Timeout waiting for cancel response for order {cl_ord_id}")
                return None

        except Exception as e:
            self.logger.error(f"Error canceling order: {e}")
            return None
        finally:
            # Clean up
            self.execution_events.pop(cancel_cl_ord_id, None)

    async def stop(self):
        """Optimized client shutdown"""
        self.running = False

        # Cancel the event processing task
        if hasattr(self, 'event_task') and self.event_task:
            self.event_task.cancel()
            try:
                await self.event_task
            except asyncio.CancelledError:
                pass

        # Gracefully logout if connected
        if self.session_id:
            try:
                logout = self._get_buffered_message()
                logout.getHeader().setField(fix.MsgType(fix.MsgType_Logout))
                fix.Session.sendToTarget(logout, self.session_id)
                self._return_message_to_pool(logout)

                # Wait briefly for logout - shorter wait
                await asyncio.sleep(0.5)  # Reduced from 2 seconds
            except Exception as e:
                self.logger.warning(f"Error during logout: {e}")

        # Stop initiator
        if self.initiator:
            self.initiator.stop()

        # Shutdown thread pool
        self.executor.shutdown(wait=False)

        # Clean up heartbeat thread
        if self.hb_thread and self.hb_thread.is_alive():
            self.hb_thread.join(timeout=0.5)

        self.logger.info("FIX client stopped")

    def _heartbeat_runner(self):
        """Optimized heartbeat runner"""
        # Pre-create heartbeat message
        heartbeat = fix.Message()
        heartbeat.getHeader().setField(fix.MsgType(fix.MsgType_Heartbeat))

        next_time = time.time() + 10

        while self.running:
            try:
                current_time = time.time()

                # Only send heartbeat if time has elapsed and we're connected
                if current_time >= next_time and self.session_id:
                    fix.Session.sendToTarget(heartbeat, self.session_id)
                    next_time = current_time + 10

                # Sleep more efficiently - adaptive sleep
                sleep_time = min(next_time - current_time, 1.0)
                if sleep_time > 0:
                    time.sleep(sleep_time)
                else:
                    time.sleep(0.1)  # Minimum sleep to prevent CPU hogging

            except Exception as e:
                self.logger.error(f"Error sending heartbeat: {e}")
                time.sleep(1)  # Sleep on error


async def main():
    """Optimized main function with asyncio"""
    # Create client
    client = FixClient("client.cfg")

    try:
        # Start client with shorter timeout
        start_time = time.time()
        if not await client.start():
            logger.error("Failed to start FIX client")
            return
        logger.info(f"Client startup took {time.time() - start_time:.3f} seconds")

        # Send buy order with better timeout handling
        start_time = time.time()
        exec_report = await asyncio.wait_for(
            client.send_new_order(
                symbol="AAPL",
                side=Side.SELL,
                order_type=OrderType.LIMIT,
                quantity=200,
                price=150,
                time_in_force=TimeInForce.DAY
            ),
            timeout=5
        )

        if exec_report:
            logger.info("Order executed successfully")
            logger.info(f"Order round trip took {time.time() - start_time:.3f} seconds")

        # Wait briefly - reduced from original
        await asyncio.sleep(0.5)



    except asyncio.TimeoutError:
        logger.error("Operation timed out")
    except Exception as e:
        logger.error(f"Error in main: {e}")
    finally:
        # Stop client with efficient timeout

        await asyncio.sleep(25)

        await asyncio.wait_for(client.stop(), timeout=2)


if __name__ == "__main__":
    # Set higher recursion limit for asyncio
    sys.setrecursionlimit(2000)

    # Run main with optimized event loop
    asyncio.run(main(), debug=False)