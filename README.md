# backup-utility
a desktop utility for backing up and restoring selected files and directories

The utility is written in Java and uses JavaFX for the GUI implementation

The utility provides a graphical directory tree for a user to select files and/or directories that they want backed up to some destination location. A destination can be another directory on the host machine, a directory in an attached thumbdrive, or a directory at some reachible network location. Obvisously, the user needs both read and write permissions for the source and destination directories. After making the selection of files and directories the user can then start the backup or restore operation.  The user can also select a number of different job options that affect the way in which backed up files are stored at the destination. The selected files and/or directories along with the selected job options can be saved as a named backup job. The following screen shot shows the various available job options:

![JobOption](/screenshots/Job.png)

If encryption is enabled, backed up files are encrypted before they are written out to the destination and the user will be required to enter a password or pass phrase.  The same password or pass phrase must be used in order for the encrypted files to be restored. The utility does not save the password anywhere in storage.  If you loose or forget the password or pass phrase, you will not be able to restore encrypted files. The utility is however capable of knowing whether a valid password or pass phrase was provided; it just doesn't know the password.

A job can be configured to have either the job name and the job execution data/time or both automatically appended to the destination path.  Jobs that have the date/time appended to the destination path can be configured to only keep the last n backup date, where n is user defined value.  For example, keep the last 3 dated backups and delete the oldest dated backup once the limit is reached.

The utility has an embedded User Guide that explains how to use the utility and how backup operations are performed in greater detail. That's a great place to start if you're interested in cloning the project. Contributor contributions that improve the utility, and very welcomed. The project also contains a snapshot directory.  This directory contains snapshots of the application 3 main windows or views.  That's also another way to quickly familiarize yourself with the application.

Please read the release notes for additional information about installing and using the application.
