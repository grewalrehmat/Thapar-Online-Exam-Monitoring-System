package com.javalab.labs;
import com.javalab.account.Course;
import com.javalab.submissions.Submission;
public record StudentLabCard(LabSession lab,Course course,String groupName,Submission submission){}
