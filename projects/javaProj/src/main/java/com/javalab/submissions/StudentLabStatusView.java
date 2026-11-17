package com.javalab.submissions;

import com.javalab.labs.LabStatus;

public record StudentLabStatusView(LabStatus status, boolean editable, boolean secureMode, long flagCount) {}
