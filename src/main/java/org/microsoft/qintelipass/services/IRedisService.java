package org.microsoft.qintelipass.services;

import org.springframework.stereotype.Service;

@Service
public interface IRedisService<T> {
    void setValue(String key, T value);
    T getValue(String key);
    void deleteValue(String key);
}
