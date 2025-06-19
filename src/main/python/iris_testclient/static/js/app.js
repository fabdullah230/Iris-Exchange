document.addEventListener('DOMContentLoaded', function() {
    // DOM Elements
    const connectBtn = document.getElementById('connect-btn');
    const disconnectBtn = document.getElementById('disconnect-btn');
    const sendOrderBtn = document.getElementById('send-order-btn');
    const connectionStatus = document.getElementById('connection-status');
    const messageList = document.getElementById('message-list');
    const messageDetail = document.getElementById('message-detail');

    // Form Elements
    const senderCompId = document.getElementById('sender-compid');
    const targetCompId = document.getElementById('target-compid');
    const symbol = document.getElementById('symbol');
    const side = document.getElementById('side');
    const orderType = document.getElementById('order-type');
    const quantity = document.getElementById('quantity');
    const price = document.getElementById('price');
    const timeInForce = document.getElementById('time-in-force');

    // Message store
    const messages = [];
    let selectedMessageId = null;

    // SocketIO connection
    const socket = io();

    // Order type change handler - toggle price field
    orderType.addEventListener('change', function() {
        const isLimit = this.value === 'LIMIT';
        price.disabled = !isLimit;
        if (!isLimit) {
            price.value = '';
        }
    });

    // Initial call to disable price for market orders
    orderType.dispatchEvent(new Event('change'));

    // Connect button click handler
    connectBtn.addEventListener('click', async function() {
        try {
            const response = await fetch('/api/connect', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    senderCompID: senderCompId.value,
                    targetCompID: targetCompId.value
                })
            });

            const result = await response.json();

            if (result.success) {
                updateConnectionStatus(true);
            } else {
                alert('Connection failed: ' + (result.error || 'Unknown error'));
            }
        } catch (error) {
            console.error('Error connecting:', error);
            alert('Error connecting: ' + error.message);
        }
    });

    // Disconnect button click handler
    disconnectBtn.addEventListener('click', async function() {
        try {
            const response = await fetch('/api/disconnect', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            const result = await response.json();

            if (result.success) {
                updateConnectionStatus(false);
            } else {
                alert('Disconnect failed: ' + (result.error || 'Unknown error'));
            }
        } catch (error) {
            console.error('Error disconnecting:', error);
            alert('Error disconnecting: ' + error.message);
        }
    });

    // Send Order button click handler
    // Send Order button click handler
    sendOrderBtn.addEventListener('click', async function() {
        if (!validateOrderForm()) {
            return;
        }

        try {
            const data = {
                symbol: symbol.value,
                side: side.value,
                orderType: orderType.value,
                quantity: parseFloat(quantity.value),
                timeInForce: timeInForce.value
            };

            if (orderType.value === 'LIMIT' && price.value) {
                data.price = parseFloat(price.value);
            }

            const response = await fetch('/api/send_order', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(data)
            });

            const result = await response.json();

            if (!result.success) {
                alert('Order failed: ' + (result.error || 'Unknown error'));
            }
            // Don't show success alert - it will appear in the message list

        } catch (error) {
            console.error('Error sending order:', error);
            alert('Error sending order: ' + error.message);
        }
    });

    // Socket.io event handlers
    socket.on('connect', function() {
        console.log('Connected to WebSocket');
        loadInitialData();
    });

    socket.on('disconnect', function() {
        console.log('Disconnected from WebSocket');
    });

    socket.on('connection_status', function(data) {
        updateConnectionStatus(data.connected);
    });

    socket.on('fix_message', function(message) {
        addMessage(message);
    });

    // Fetch initial data when page loads
    async function loadInitialData() {
        try {
            // Get connection status
            const statusResponse = await fetch('/api/status');
            const statusData = await statusResponse.json();
            updateConnectionStatus(statusData.connected);

            // Get message history
            const messagesResponse = await fetch('/api/messages');
            const messagesData = await messagesResponse.json();

            // Clear existing messages
            messages.length = 0;
            messageList.innerHTML = '';

            // Add messages to the UI
            messagesData.messages.forEach(message => {
                addMessage(message);
            });
        } catch (error) {
            console.error('Error loading initial data:', error);
        }
    }

    // Update connection status in UI
    function updateConnectionStatus(connected) {
        if (connected) {
            connectionStatus.textContent = 'Connected';
            connectionStatus.classList.add('connected');
            connectBtn.disabled = true;
            disconnectBtn.disabled = false;
            sendOrderBtn.disabled = false;
        } else {
            connectionStatus.textContent = 'Disconnected';
            connectionStatus.classList.remove('connected');
            connectBtn.disabled = false;
            disconnectBtn.disabled = true;
            sendOrderBtn.disabled = true;
        }
    }

    // Add message to UI
    function addMessage(message) {
        // Generate a unique ID for the message
        message.id = Date.now() + Math.random().toString(36).substr(2, 9);

        // Add to messages array
        messages.unshift(message); // Add to beginning - newest first

        // Create message element
        const messageItem = document.createElement('div');
        messageItem.className = 'message-item';
        messageItem.dataset.id = message.id;

        // Create direction indicator
        const direction = document.createElement('div');
        direction.className = `direction ${message.direction || 'unknown'}`;
        messageItem.appendChild(direction);

        // Create message content
        const content = document.createElement('div');
        content.className = 'message-content';

        // Create message header
        const header = document.createElement('div');
        header.className = 'message-header';

        // Message type
        const type = document.createElement('div');
        type.className = 'message-type';
        type.textContent = getMsgTypeName(message.msgType) || 'Unknown';
        header.appendChild(type);

        // Message timestamp
        const timestamp = document.createElement('div');
        timestamp.className = 'message-timestamp';
        timestamp.textContent = message.timestamp;
        header.appendChild(timestamp);

        content.appendChild(header);

        // Message summary
        const summary = document.createElement('div');
        summary.className = 'message-summary';
        summary.textContent = getMessageSummary(message);
        content.appendChild(summary);

        messageItem.appendChild(content);

        // Add click handler
        messageItem.addEventListener('click', function() {
            // Deselect previous
            const selected = messageList.querySelector('.selected');
            if (selected) {
                selected.classList.remove('selected');
            }

            // Select this
            this.classList.add('selected');
            selectedMessageId = this.dataset.id;

            // Find the corresponding message
            const selectedMessage = messages.find(m => m.id === selectedMessageId);
            if (selectedMessage) {
                // Show details
                showMessageDetails(selectedMessage);
            }
        });

        // Add to list (at beginning)
        if (messageList.firstChild) {
            messageList.insertBefore(messageItem, messageList.firstChild);
        } else {
            messageList.appendChild(messageItem);
        }

        // Limit number of displayed messages for performance
        if (messageList.children.length > 1000) {
            messageList.removeChild(messageList.lastChild);
        }
    }

    // Show message details in right panel
    function showMessageDetails(message) {
        // Clear previous content
        messageDetail.innerHTML = '';

        // Create header section
        const headerSection = document.createElement('div');
        headerSection.className = 'detail-section';

        const headerTitle = document.createElement('div');
        headerTitle.className = 'detail-section-title';
        headerTitle.textContent = 'Message Information';
        headerSection.appendChild(headerTitle);

        // Add common fields
        addFieldRow(headerSection, 'Type', getMsgTypeName(message.msgType) || 'Unknown');
        addFieldRow(headerSection, 'Direction', message.direction || 'Unknown');
        addFieldRow(headerSection, 'Timestamp', message.timestamp || '');

        messageDetail.appendChild(headerSection);

        // Create data section
        const dataSection = document.createElement('div');
        dataSection.className = 'detail-section';

        const dataTitle = document.createElement('div');
        dataTitle.className = 'detail-section-title';
        dataTitle.textContent = 'Message Data';
        dataSection.appendChild(dataTitle);

        // Add special fields based on message type
        if (message.msgType === '8') { // Execution Report
            // Order information
            addFieldRow(dataSection, 'Client Order ID', message.clOrdID || '');
            addFieldRow(dataSection, 'Order ID', message.orderID || '');
            addFieldRow(dataSection, 'Symbol', message.symbol || '');
            addFieldRow(dataSection, 'Side', message.sideText || message.side || '');

            // Execution information
            addFieldRow(dataSection, 'Order Status', message.ordStatusText || message.ordStatus || '');
            addFieldRow(dataSection, 'Execution ID', message.execID || '');
            addFieldRow(dataSection, 'Execution Type', message.execTypeText || message.execType || '');
            addFieldRow(dataSection, 'Transaction Time', message.transactTime || '');

            // Quantity information
            addFieldRow(dataSection, 'Order Quantity', message.orderQty || '');
            addFieldRow(dataSection, 'Filled Quantity', message.cumQty || '');
            addFieldRow(dataSection, 'Remaining Quantity', message.leavesQty || '');
            addFieldRow(dataSection, 'Last Quantity', message.lastQty || '');

            // Price information
            addFieldRow(dataSection, 'Price', message.price || '');
            addFieldRow(dataSection, 'Last Price', message.lastPx || '');
            addFieldRow(dataSection, 'Average Price', message.avgPx || '');

            // Other information
            if (message.text) {
                addFieldRow(dataSection, 'Text', message.text);
            }
        } else if (message.msgType === 'D') { // New Order
            addFieldRow(dataSection, 'Client Order ID', message.clOrdID || '');
            addFieldRow(dataSection, 'Symbol', message.symbol || '');
            addFieldRow(dataSection, 'Side', message.sideText || message.side || '');
            addFieldRow(dataSection, 'Order Type', message.ordTypeText || message.ordType || '');
            addFieldRow(dataSection, 'Quantity', message.orderQty || '');
            addFieldRow(dataSection, 'Price', message.price || '');
            addFieldRow(dataSection, 'Time in Force', message.timeInForceText || message.timeInForce || '');
            addFieldRow(dataSection, 'Transaction Time', message.transactTime || '');
        } else {
            // For other message types, just display all fields
            const excludeKeys = ['id', 'raw', 'direction', 'timestamp', 'msgType'];

            Object.keys(message).forEach(key => {
                if (!excludeKeys.includes(key) && message[key] !== undefined) {
                    addFieldRow(dataSection, getFriendlyFieldName(key), message[key]);
                }
            });
        }

        messageDetail.appendChild(dataSection);

        // Create raw section
        const rawSection = document.createElement('div');
        rawSection.className = 'detail-section';

        const rawTitle = document.createElement('div');
        rawTitle.className = 'detail-section-title';
        rawTitle.textContent = 'Raw Message';
        rawSection.appendChild(rawTitle);

        const rawContent = document.createElement('div');
        rawContent.className = 'raw-content';
        rawContent.textContent = message.raw || '';
        rawSection.appendChild(rawContent);

        messageDetail.appendChild(rawSection);
    }

    // Add a field row to detail section
    function addFieldRow(container, name, value) {
        const row = document.createElement('div');
        row.className = 'field-row';

        const nameEl = document.createElement('div');
        nameEl.className = 'field-tag';
        nameEl.textContent = name;
        row.appendChild(nameEl);

        const valueEl = document.createElement('div');
        valueEl.className = 'field-value';
        valueEl.textContent = value;
        row.appendChild(valueEl);

        container.appendChild(row);
    }

    // Get friendly message type name
    function getMsgTypeName(type) {
        const msgTypes = {
            '0': 'Heartbeat',
            '1': 'Test Request',
            '2': 'Resend Request',
            '3': 'Reject',
            '4': 'Sequence Reset',
            '5': 'Logout',
            '6': 'IOI',
            '7': 'Advertisement',
            '8': 'Execution Report',
            '9': 'Order Cancel Reject',
            'A': 'Logon',
            'D': 'New Order - Single',
            'F': 'Order Cancel Request',
            'G': 'Order Cancel/Replace Request'
        };

        return type ? (msgTypes[type] || `Type ${type}`) : 'Unknown';
    }

    // Get friendly field name
    function getFriendlyFieldName(tagName) {
        const fieldNames = {
            'clOrdID': 'Client Order ID',
            'orderID': 'Order ID',
            'execID': 'Execution ID',
            'symbol': 'Symbol',
            'side': 'Side',
            'sideText': 'Side',
            'ordType': 'Order Type',
            'ordTypeText': 'Order Type',
            'orderQty': 'Quantity',
            'price': 'Price',
            'ordStatus': 'Order Status',
            'ordStatusText': 'Order Status',
            'transactTime': 'Transaction Time',
            'execType': 'Execution Type',
            'execTypeText': 'Execution Type',
            'lastQty': 'Last Quantity',
            'lastPx': 'Last Price',
            'leavesQty': 'Leaves Quantity',
            'cumQty': 'Cumulative Quantity',
            'avgPx': 'Average Price',
            'timeInForce': 'Time in Force',
            'timeInForceText': 'Time in Force',
            'text': 'Text'
        };

        return fieldNames[tagName] || tagName;
    }

    // Create message summary
    function getMessageSummary(message) {
        switch (message.msgType) {
            case 'D': // New Order - Single
                const price = message.price ? `@ ${message.price}` : 'MKT';
                return `${message.symbol || ''} ${message.sideText || message.side || ''} ${message.orderQty || '0'} ${price}`;
            case '8': // Execution Report
                let summary = `${message.symbol || ''} ${message.sideText || message.side || ''} ${message.orderQty || '0'} `;
                if (message.ordStatusText) {
                    summary += `Status: ${message.ordStatusText}`;
                } else if (message.ordStatus) {
                    summary += `Status: ${getOrderStatusText(message.ordStatus)}`;
                }

                // Add fill information for fills
                if (message.ordStatus === '2' || message.ordStatus === '1') {
                    const fillQty = message.lastQty || '0';
                    const fillPrice = message.lastPx || message.price || '0';
                    summary += ` (${fillQty} @ ${fillPrice})`;
                }

                return summary;
            case 'F': // Order Cancel Request
                return `Cancel ${message.symbol || ''} ${message.sideText || message.side || ''} ${message.orderQty || '0'}`;
            case '9': // Order Cancel Reject
                return `Cancel Rejected: ${message.clOrdID || ''}`;
            case 'A': // Logon
                return 'Logon message';
            case '5': // Logout
                return 'Logout message';
            case '0': // Heartbeat
                return 'Heartbeat';
            default:
                return message.raw ? message.raw.substring(0, 50) + '...' : 'Unknown message';
        }
    }

// Helper function to get side name
    function getSideName(side) {
        if (!side) return '';
        if (side === '1') return 'BUY';
        if (side === '2') return 'SELL';
        return side;
    }

    // Get friendly order status
    function getOrderStatusText(status) {
        const statuses = {
            '0': 'New',
            '1': 'Partially Filled',
            '2': 'Filled',
            '3': 'Done for Day',
            '4': 'Canceled',
            '5': 'Replaced',
            '6': 'Pending Cancel',
            '7': 'Stopped',
            '8': 'Rejected',
            '9': 'Suspended',
            'A': 'Pending New',
            'B': 'Calculated',
            'C': 'Expired',
            'D': 'Accepted',
            'E': 'Pending Replace'
        };

        return status ? (statuses[status] || `Status ${status}`) : 'Unknown';
    }

    // Validate order form
    function validateOrderForm() {
        if (!symbol.value) {
            alert('Symbol is required');
            symbol.focus();
            return false;
        }

        if (!quantity.value || isNaN(parseFloat(quantity.value)) || parseFloat(quantity.value) <= 0) {
            alert('Quantity must be a positive number');
            quantity.focus();
            return false;
        }

        if (orderType.value === 'LIMIT' && (!price.value || isNaN(parseFloat(price.value)) || parseFloat(price.value) <= 0)) {
            alert('Price must be a positive number for limit orders');
            price.focus();
            return false;
        }

        return true;
    }

    // Initial load
    loadInitialData();
});