package kz.studhub.service;

/** A course with student-owned work must not be removed. */
public class CourseInUseException extends RuntimeException {
    public CourseInUseException() {
        super("Курс нельзя удалить: к нему привязаны задания или учебные шаги пользователей. Попросите владельцев отвязать задания и удалить учебные шаги.");
    }
}
