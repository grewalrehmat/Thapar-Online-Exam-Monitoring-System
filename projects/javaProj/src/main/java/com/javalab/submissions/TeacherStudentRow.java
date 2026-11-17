package com.javalab.submissions;
import com.javalab.account.UserAccount;
import java.util.List;
public record TeacherStudentRow(UserAccount student,Submission submission,List<TeacherActivityView> activity){}
