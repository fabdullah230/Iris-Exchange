package com.iris.iris_appserver.validation;

import com.iris.iris_appserver.model.ValidationResult;
import com.iris.iris_appserver.service.InstrumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.field.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderValidationService {

    private final InstrumentService instrumentService;

    /**
     * Validates a New Order Single
     */
    public ValidationResult validateNewOrderSingle(Message message) {
        try {
            // Extract fields
            String symbol = message.getString(Symbol.FIELD);

            // Validate instrument
            if (!instrumentService.isValidInstrument(symbol)) {
                return ValidationResult.invalid("Invalid instrument: " + symbol);
            }

            // Validate price for limit orders
            char orderType = message.getChar(OrdType.FIELD);
            if (orderType == OrdType.LIMIT) {
                if (!message.isSetField(Price.FIELD)) {
                    return ValidationResult.invalid("Price is required for limit orders");
                }
                double price = message.getDouble(Price.FIELD);

                // Check price range (within 10% of last trade)
                if (!instrumentService.isValidPrice(symbol, price)) {
                    return ValidationResult.invalid("Price outside acceptable range");
                }
            }

            // Validate quantity
            double quantity = message.getDouble(OrderQty.FIELD);
            if (quantity <= 0) {
                return ValidationResult.invalid("Invalid quantity: must be positive");
            }

            return ValidationResult.valid();
        } catch (FieldNotFound e) {
            log.error("Missing required field in new order: {}", e.getMessage());
            return ValidationResult.invalid("Missing required field: " + e.getMessage());
        } catch (Exception e) {
            log.error("Validation error: {}", e.getMessage());
            return ValidationResult.invalid("Validation error: " + e.getMessage());
        }
    }

    /**
     * Validates an Order Replace Request
     */
    public ValidationResult validateOrderReplaceRequest(Message message) {
        try {
            // Extract fields
            String symbol = message.getString(Symbol.FIELD);
            String origClOrdId = message.getString(OrigClOrdID.FIELD);

            // Validate instrument
            if (!instrumentService.isValidInstrument(symbol)) {
                return ValidationResult.invalid("Invalid instrument: " + symbol);
            }

            // Validate price for limit orders
            char orderType = message.getChar(OrdType.FIELD);
            if (orderType == OrdType.LIMIT) {
                double price = message.getDouble(Price.FIELD);

                // Check price range (within 10% of last trade)
                if (!instrumentService.isValidPrice(symbol, price)) {
                    return ValidationResult.invalid("Price outside acceptable range");
                }
            }

            // Here we would typically also validate that the original order exists
            // For now, we'll assume it does

            return ValidationResult.valid();
        } catch (FieldNotFound e) {
            log.error("Missing required field in order replace request: {}", e.getMessage());
            return ValidationResult.invalid("Missing required field: " + e.getMessage());
        } catch (Exception e) {
            log.error("Validation error: {}", e.getMessage());
            return ValidationResult.invalid("Validation error: " + e.getMessage());
        }
    }

    /**
     * Validates an Order Cancel Request
     */
    public ValidationResult validateOrderCancelRequest(Message message) {
        try {
            // Extract fields
            String symbol = message.getString(Symbol.FIELD);
            String origClOrdId = message.getString(OrigClOrdID.FIELD);

            // Validate instrument
            if (!instrumentService.isValidInstrument(symbol)) {
                return ValidationResult.invalid("Invalid instrument: " + symbol);
            }

            // Here we would typically also validate that the original order exists
            // For now, we'll assume it does

            return ValidationResult.valid();
        } catch (FieldNotFound e) {
            log.error("Missing required field in cancel request: {}", e.getMessage());
            return ValidationResult.invalid("Missing required field: " + e.getMessage());
        } catch (Exception e) {
            log.error("Validation error: {}", e.getMessage());
            return ValidationResult.invalid("Validation error: " + e.getMessage());
        }
    }

    /**
     * Validates a Mass Cancel Request
     */
    public ValidationResult validateOrderMassCancelRequest(Message message) {
        try {
            int cancelRequestType = message.getInt(MassCancelRequestType.FIELD);

            // If canceling by security, validate the symbol
            if (cancelRequestType == MassCancelRequestType.CANCEL_ALL_ORDERS) {
                if (!message.isSetField(Symbol.FIELD)) {
                    return ValidationResult.invalid("Symbol is required for security-specific cancel");
                }

                String symbol = message.getString(Symbol.FIELD);
                if (!instrumentService.isValidInstrument(symbol)) {
                    return ValidationResult.invalid("Invalid instrument: " + symbol);
                }
            }

            return ValidationResult.valid();
        } catch (FieldNotFound e) {
            log.error("Missing required field in mass cancel request: {}", e.getMessage());
            return ValidationResult.invalid("Missing required field: " + e.getMessage());
        } catch (Exception e) {
            log.error("Validation error: {}", e.getMessage());
            return ValidationResult.invalid("Validation error: " + e.getMessage());
        }
    }
}