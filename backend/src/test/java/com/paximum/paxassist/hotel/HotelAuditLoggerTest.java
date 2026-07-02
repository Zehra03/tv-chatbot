package com.paximum.paxassist.hotel;

import com.paximum.paxassist.audit.AuditLogModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class HotelAuditLoggerTest {

    @Mock
    private AuditLogModule auditLogModule;

    @InjectMocks
    private HotelAuditLogger hotelAuditLogger;

    @Test
    void shouldLogActionAsynchronously() {
        // Given
        String action = "SEARCH_HOTEL";
        String details = "Destination: Antalya";

        // When
        hotelAuditLogger.logActionAsync(action, details);

        // Then
        verify(auditLogModule, times(1)).logActionAsync(eq(action), eq(details));
    }

    @Test
    void shouldLogExceptionsAndThrowRuntime() {
        // Given
        Exception ex = new RuntimeException("API Connection Failed");

        // When/Then
        assertThrows(RuntimeException.class, () -> hotelAuditLogger.handleExceptionAndLog(ex));
        
        verify(auditLogModule, times(1)).logErrorAsync(eq("HOTEL_MODULE_ERROR"), anyString());
    }
}
