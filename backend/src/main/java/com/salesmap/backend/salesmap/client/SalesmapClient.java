package com.salesmap.backend.salesmap.client;

import com.salesmap.backend.salesmap.client.dto.SalesmapApiRegisterRequest;
import com.salesmap.backend.salesmap.client.dto.SalesmapApiRegisterResponse;

public interface SalesmapClient {

    SalesmapApiRegisterResponse register(SalesmapApiRegisterRequest request);
}
