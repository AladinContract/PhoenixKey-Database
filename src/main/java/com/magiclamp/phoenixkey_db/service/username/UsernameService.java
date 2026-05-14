package com.magiclamp.phoenixkey_db.service.username;

import com.magiclamp.phoenixkey_db.dto.username.UsernameResolveResponse;
import com.magiclamp.phoenixkey_db.dto.username.UsernameSetRequest;
import com.magiclamp.phoenixkey_db.dto.username.UsernameSetResponse;

public interface UsernameService {
    UsernameSetResponse setUsername(String userDid, UsernameSetRequest request);
    UsernameResolveResponse resolveByUsername(String username);
}
