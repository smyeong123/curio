package com.curio.admin.port.in;

import java.util.Map;

public interface AdminOperationsUseCase {
    Map<String, Object> triggerEmailSend();
    Map<String, Object> triggerCleanup();
}
