package com.mac.usermanagement.service;

import com.mac.usermanagement.entities.model.ErrorAlert;

public interface ErrorAlertNotifier {

    void send(ErrorAlert alert);
}
