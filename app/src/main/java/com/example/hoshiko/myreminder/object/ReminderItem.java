package com.example.hoshiko.myreminder.object;

// Class for recycler view items
public  class ReminderItem {
    public String mTitle;
    public String mDateTime;
    public String mRepeat;
    public String mRepeatNo;
    public String mRepeatType;
    public String mActive;

    public ReminderItem(String Title, String DateTime, String Repeat, String RepeatNo, String RepeatType, String Active) {
        this.mTitle = Title;
        this.mDateTime = DateTime;
        this.mRepeat = Repeat;
        this.mRepeatNo = RepeatNo;
        this.mRepeatType = RepeatType;
        this.mActive = Active;
    }
}
