package com.javalab.submissions;
import com.javalab.account.UserAccount;
import com.javalab.labs.LabSession;
import java.util.List;
public record TeacherStudentReview(UserAccount student,LabSession lab,Submission submission,List<RunRecord> runs,List<GradeRecord> gradeHistory,List<TeacherActivityView> activity){}
