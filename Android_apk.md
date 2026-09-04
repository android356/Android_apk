Android Auto Deploy & InfinityFree Sync

Complete Product Requirements Document (PRD)

1. Product Overview

Build a complete, production-oriented Android application named:

Android Auto Deploy

The application must automatically synchronize a user-selected local project folder from Android internal storage directly to an InfinityFree hosting account.

The primary purpose is to create a seamless deployment workflow between an AI coding environment and live web hosting.

The intended workflow is:

AI Coding Tool / Google Antigravity
↓
User provides a development prompt
↓
AI creates, modifies, renames, or deletes source-code files
↓
Changes occur inside a user-selected Android project folder
↓
Android Auto Deploy detects the changes
↓
Files are checked until stable
↓
Changed files are added to a persistent sync queue
↓
Files are uploaded directly to InfinityFree
↓
Remote website source code is updated automatically

The user must not need to manually open the InfinityFree File Manager every time source code changes.

The Android application itself must act as the control interface.

There must be no separate custom web admin panel.

---

2. Product Goal

The main goal is to provide an automatic deployment system where a user can work with an AI coding tool that writes files into a selected project folder, while the Android application continuously detects those changes and synchronizes the required files directly to InfinityFree hosting.

The system must support:

- New files
- Modified files
- Nested folders
- Large project structures
- Frequent AI-generated changes
- Automatic deployment
- Manual synchronization
- Persistent upload queue
- Retry handling
- Sync logs
- Temporary old-version backups
- Future rollback support

The system must prioritize:

1. Reliability
2. Correct file detection
3. Prevention of duplicate uploads
4. Efficient synchronization
5. Android compatibility
6. Recovery after application restart

---

3. Core User Flow

The complete user flow must be:

Step 1 — Install Application

The user installs the Android APK.

Step 2 — Select Project Folder

The user selects the folder where the AI coding tool creates and modifies project files.

Example:

Project/

index.html

css/

js/

assets/

components/

The application must receive permission to access the selected folder.

Step 3 — Configure InfinityFree

The user enters the hosting connection details required for supported file transfer.

The user configures:

- Connection name
- FTP host
- FTP username
- FTP password
- Remote root directory

Example:

/htdocs/

Step 4 — Test Connection

The application tests the connection.

If successful:

Display:

Connected Successfully

If unsuccessful:

Display a clear error.

Step 5 — Initial Synchronization

The user can perform the first synchronization.

All files in the selected project folder must be uploaded while preserving the directory structure.

Step 6 — Enable Auto Sync

The user turns:

Auto Sync ON

After this point:

AI Coding Tool
↓
Creates or modifies files
↓
Android Auto Deploy detects changes
↓
Files become stable
↓
Changed files enter the queue
↓
Files upload automatically
↓
InfinityFree website source updates

---

4. Technology Requirements

Build the application as a proper Android application.

Recommended architecture:

- Kotlin
- Native Android APIs
- Jetpack Compose
- Room Database
- DataStore
- Android Storage Access Framework
- Secure credential storage
- Foreground/active synchronization service where required
- Background workers for appropriate non-continuous work

The implementation must respect Android's current background execution restrictions.

The application must not falsely claim guaranteed 30-second background execution when the Android operating system may suspend or stop normal background processing.

If frequent monitoring is required, use an appropriate active or foreground synchronization mechanism with transparent user-visible status where required.

---

5. Folder Selection and Permissions

On first launch, the application must allow the user to select a project folder.

Use Android's Storage Access Framework.

Required flow:

Open Application
↓
Select Project Folder
↓
Android Folder Picker
↓
User selects folder
↓
User grants access
↓
Persist folder URI permission
↓
Application saves project configuration

Requirements:

- Persist folder access.
- Restore access after application restart when permission remains valid.
- Support recursive access to files and subfolders within the selected folder.
- Allow the user to change the selected folder.
- Detect revoked permissions.
- Stop synchronization safely when access is unavailable.
- Allow the user to select a new folder.

Do not request unnecessary full-device storage access when selected-folder access is sufficient.

---

6. Project Structure Monitoring

The selected project folder may contain:

- Individual files
- Nested folders
- Thousands of files
- Very large directory structures
- AI-generated files
- HTML
- CSS
- JavaScript
- PHP
- Images
- JSON
- Configuration files
- Other website project files

The system must detect:

- New file creation
- Existing file modification
- File content change
- File size change
- Last modified timestamp change
- File rename
- File movement
- File deletion
- Folder creation
- Folder rename
- Folder movement
- Folder deletion

The exact relative directory structure must be preserved.

Example:

Local Project:

Project/
├── index.html
├── css/
│   └── style.css
├── js/
│   └── app.js
└── assets/
    └── images/
        └── logo.png

Remote InfinityFree Structure:

/htdocs/
├── index.html
├── css/
│   └── style.css
├── js/
│   └── app.js
└── assets/
    └── images/
        └── logo.png

---

7. Change Detection System

The system must not upload the entire project every time.

Implement an efficient change detection architecture.

Use two layers.

Layer 1 — Primary Change Detection

Use Android-supported file observation or change-event mechanisms where technically supported.

When a file change is detected:

Add the affected file to a pending change set.

Do not immediately upload the file.

The system must wait until the file becomes stable.

Layer 2 — Periodic Reconciliation

Perform a recursive reconciliation scan while Auto Sync is enabled.

Default interval:

30 seconds

During each scan:

1. Read the current project state.
2. Compare current metadata with saved metadata.
3. Detect missed changes.
4. Identify new files.
5. Identify modified files.
6. Identify deleted files where deletion sync is enabled.
7. Add only required operations to the synchronization queue.

Do not upload unchanged files.

---

8. File Metadata Tracking

Maintain metadata for every tracked file.

Store:

- Project ID
- Relative path
- File name
- File type
- File size
- Last modified timestamp
- Last successful synchronization time
- Sync status
- Exists state
- Optional checksum/hash
- Version information where applicable

Basic change comparison must use:

- Relative path
- File size
- Last modified timestamp

If metadata indicates a change:

The file must be synchronized.

If necessary, optional hashing may be used later for verification.

Do not calculate expensive hashes for every unchanged file during every 30-second scan.

---

9. Large Folder Performance

The application must be designed to handle large project structures efficiently.

Avoid:

- Uploading every file during every scan.
- Loading all file content into memory.
- Calculating hashes unnecessarily.
- Blocking the main UI thread.
- Creating duplicate upload tasks.

Use:

- Database indexes
- Background processing
- Incremental scanning
- Event-driven detection
- Metadata comparison
- Persistent queue operations

The architecture must remain stable even when the selected folder contains a very large number of files and folders.

---

10. File Stability and Debounce System

AI coding tools may write the same file multiple times within a short period.

Example:

10:00:00 — app.js modified

10:00:01 — app.js modified again

10:00:02 — app.js modified again

The application must not upload three unnecessary versions.

Required flow:

File Changed
↓
Add to Pending Change Set
↓
Wait for debounce period
↓
Check size and modification timestamp
↓
Still changing?
↓
Wait again
↓
Stable?
↓
Add to Sync Queue
↓
Upload once

The default stability period can initially be approximately:

3–5 seconds

The implementation must make this configurable for future versions.

---

11. Initial Synchronization

When the user first configures a project:

1. Scan the complete project folder.
2. Create metadata records.
3. Identify all files as NOT_SYNCED.
4. Create required remote directories.
5. Upload all project files.
6. Confirm successful uploads.
7. Update synchronization metadata.

After the initial sync:

Only changed files should normally be uploaded.

---

12. Manual Sync

Provide a large and clearly visible:

SYNC NOW

button.

When pressed:

1. Scan the project.
2. Compare current metadata.
3. Detect new files.
4. Detect modified files.
5. Detect relevant deletions if enabled.
6. Add operations to the persistent queue.
7. Process the queue.
8. Update metadata.
9. Display results.

Manual Sync must be fully functional before advanced automatic synchronization features are considered complete.

---

13. Auto Sync

Provide:

AUTO SYNC ON / OFF

When ON:

- Monitor file changes.
- Perform periodic reconciliation.
- Detect stable changes.
- Add changed files to the queue.
- Automatically upload them.

When OFF:

- Stop active monitoring.
- Stop automatic uploads.
- Preserve project configuration and queue state.

Manual Sync must remain available when Auto Sync is OFF.

---

14. Persistent Synchronization Queue

Create a persistent queue using the local database.

Every queue item must contain:

- Unique ID
- Project ID
- Relative path
- Operation type
- Status
- Retry count
- Created timestamp
- Last attempt timestamp
- Error message

Supported operations:

- UPLOAD
- CREATE_DIRECTORY
- DELETE_FILE
- DELETE_DIRECTORY
- RETRY
- ROLLBACK

Queue statuses:

- PENDING
- PREPARING
- UPLOADING
- SUCCESS
- FAILED
- RETRYING
- CANCELLED

The queue must survive:

- App restart
- Temporary internet loss
- Temporary server failure

Duplicate protection:

If the same file changes multiple times before upload:

Do not create multiple duplicate queue entries.

Keep the latest required synchronization state.

---

15. InfinityFree Synchronization

The application must directly synchronize files to the configured InfinityFree hosting destination using the supported connection method and credentials configured by the user.

The application must support:

New File

Create required remote directories.

Upload the new file.

Modified File

Detect the modification.

Create temporary backup information where the backup system is enabled.

Upload the new stable version.

Verify completion.

Update metadata.

New Folder

Create the required remote directory.

Upload contained files.

Deleted File

Provide a setting:

Sync Deletions to Server

If enabled:

Local deletion
↓
Detect deletion
↓
Queue remote deletion
↓
Delete corresponding remote file

If disabled:

The remote file must remain untouched.

Deletion synchronization should be implemented after the core MVP upload flow is stable.

---

16. Temporary Old-Version Backup System

When a file is about to be replaced, preserve the previous known version.

The default retention period must be:

1 hour

Example:

10:00 — Version A is live.

10:20 — index.html changes.

Before Version B replaces the current version:

1. Preserve Version A.
2. Record backup metadata.
3. Upload Version B.
4. Keep Version A temporarily available.
5. Set the expiration time to 1 hour.
6. Automatically clean up expired backups.

Each temporary backup must contain:

- Backup ID
- Project ID
- Relative file path
- Backup creation time
- Original modification time
- Version identifier
- Expiration time
- Sync operation reference
- Backup status

The retention period must be configurable in future versions.

The system must not permanently accumulate unlimited backup files.

Automatic cleanup is required.

---

17. Rollback

While a temporary backup is valid:

The user must be able to:

- View recent available backups.
- Identify the original file path.
- See backup creation time.
- Restore a previous version.
- Re-upload the restored version to InfinityFree.

Rollback flow:

Select Backup
↓
Show Confirmation
↓
Restore Selected Version
↓
Create Sync Queue Operation
↓
Upload Restored Version
↓
Update Metadata
↓
Record Activity Log

Never overwrite the current version silently.

Explicit user confirmation is required.

Rollback can be implemented as a post-MVP feature after the core synchronization workflow is stable.

---

18. Local Database

Use a structured local database.

Projects

Fields:

- id
- project_name
- folder_uri
- created_at
- updated_at
- active

MVP supports one active project.

HostingConnections

Fields:

- id
- project_id
- connection_name
- server
- username
- encrypted_password_reference
- remote_root_directory
- created_at
- updated_at

MVP supports one active connection.

FileMetadata

Fields:

- id
- project_id
- relative_path
- item_type
- file_size
- last_modified
- last_synced_at
- sync_status
- optional_hash
- exists

SyncQueue

Fields:

- id
- project_id
- relative_path
- operation
- status
- retry_count
- created_at
- last_attempt_at
- error_message

TemporaryBackups

Fields:

- id
- project_id
- relative_path
- backup_path
- created_at
- expires_at
- version_identifier
- status

SyncHistory

Fields:

- id
- project_id
- operation
- relative_path
- started_at
- completed_at
- result
- error_message

Create appropriate database indexes for:

- project_id
- relative_path
- sync_status
- expires_at
- queue status

---

19. Dashboard

The dashboard must open immediately when the application starts.

Display:

Sync Status

ON / OFF

Selected Project

Project name and selected folder.

Hosting Status

Connected / Not Connected / Error.

Total Files

Dynamic count.

Total Folders

Dynamic count.

Last Scan

Timestamp.

Last Successful Sync

Timestamp.

Current Activity

Examples:

- Idle
- Scanning
- Detecting Changes
- Preparing Upload
- Uploading
- Retrying
- Error

Upload Queue

Pending count.

Failed Items

Failed count.

Temporary Backups

Available backup count.

Main actions:

- Start Auto Sync
- Stop Auto Sync
- Sync Now
- Change Folder
- Connection Settings
- Sync Settings
- Activity Log
- Failed Uploads
- Temporary Backups

The design must be:

- Mobile-first
- Clean
- Responsive
- Touch-friendly
- Android-style
- Properly aligned
- Easy to understand

Avoid excessive empty cards or unnecessarily large UI elements.

Do not use a heavily black background by default.

All text and controls must fit correctly across Android screen sizes.

---

20. Activity Log

Create detailed synchronization history.

Examples:

11:30:00 — File change detected: index.html

11:30:03 — File stabilized.

11:30:04 — Previous version backup created.

11:30:05 — Upload started.

11:30:08 — Upload successful.

11:30:09 — Metadata updated.

Additional examples:

11:35:00 — css/style.css modified.

11:35:04 — Added to upload queue.

11:35:07 — Upload failed.

11:35:30 — Retry successful.

Logs must include:

- Timestamp
- Operation
- File path
- Result
- Error message when applicable

Provide filters:

- Success
- Failed
- Upload
- Delete
- Backup
- Rollback

Never expose passwords or sensitive credentials in logs.

---

21. Error Handling

Handle:

- No internet connection
- Invalid credentials
- Connection failure
- Authentication failure
- Remote directory unavailable
- Folder permission revoked
- File permission failure
- File deleted before upload
- File changed during upload
- Partial upload failure
- Application restart
- Temporary server failure

Retry behavior:

For MVP:

Maximum automatic retries:

3

After the maximum retry count:

Mark the item as:

FAILED

Provide:

- Retry Failed
- Retry All Failed

The application must not retry infinitely.

---

22. Security Requirements

Sensitive hosting credentials must be securely stored.

Requirements:

- Use secure/encrypted credential storage.
- Never hardcode credentials.
- Never log passwords.
- Do not expose passwords in activity logs.
- Require explicit user configuration.
- Keep file access limited to the selected project folder where possible.
- Validate connection configuration.
- Avoid exposing sensitive data through exported application logs.

---

23. MVP Implementation Plan

The first release must focus on proving the complete end-to-end deployment flow.

The primary MVP success flow is:

AI Coding Tool changes a file
↓
Android application detects the change
↓
File enters pending state
↓
File becomes stable
↓
Changed file enters persistent queue
↓
File uploads successfully
↓
InfinityFree source code updates
↓
Metadata updates
↓
Success appears in activity log

---

Phase 1 — Application Foundation

Build:

- Android project structure
- Navigation
- Dashboard
- Room database
- DataStore
- Secure storage
- Synchronization architecture

Initial screens:

1. Dashboard
2. Select Project Folder
3. Hosting Connection Setup
4. Sync Settings
5. Activity Log

---

Phase 2 — Folder Access

Implement:

- Storage Access Framework
- Folder picker
- Persistent URI permission
- Selected folder storage
- Permission validation
- Change folder functionality

MVP limitation:

One active project folder.

---

Phase 3 — Hosting Connection

Implement:

- Connection name
- FTP host
- Username
- Password
- Remote root directory
- Test Connection
- Save Connection

MVP limitation:

One active hosting connection.

---

Phase 4 — Initial Scan

Perform a recursive project scan.

Store:

- Relative path
- Size
- Last modified timestamp
- Sync status

Mark initial files as:

NOT_SYNCED

---

Phase 5 — Manual Sync

Implement:

SYNC NOW

Required workflow:

Scan
↓
Compare Metadata
↓
Identify New/Modified Files
↓
Queue Files
↓
Create Remote Directories
↓
Upload
↓
Update Metadata
↓
Show Result

Manual synchronization must work reliably before Auto Sync is considered complete.

---

Phase 6 — Persistent Queue

Implement:

- PENDING
- UPLOADING
- SUCCESS
- FAILED

Persist queue state across app restarts.

Prevent duplicate queue entries.

If the same file changes repeatedly:

Keep the latest pending state.

---

Phase 7 — Auto Sync

Implement:

- File change detection where supported.
- Pending change tracking.
- Debounce.
- Stability verification.
- Automatic queue processing.
- 30-second reconciliation scan.

MVP detection criteria:

- Relative path
- File size
- Last modified timestamp

Do not hash every file during every scan.

---

Phase 8 — File Stability

Default debounce:

Approximately 3–5 seconds.

Workflow:

Change detected
↓
Wait
↓
Re-check metadata
↓
Still changing?
↓
Wait again
↓
Stable?
↓
Upload

---

Phase 9 — Dashboard Completion

Ensure the dashboard displays:

- Sync ON/OFF
- Selected folder
- Hosting connection status
- Total files
- Total folders
- Last scan
- Last sync
- Current activity
- Pending uploads
- Failed uploads

---

Phase 10 — Logging and Recovery

Implement:

- Sync logs
- Error logs
- Retry logs
- Restart recovery
- Persistent pending operations

---

24. MVP Scope

The first working release must include:

- One project folder
- One hosting connection
- Folder permission
- Initial recursive scan
- Manual Sync
- Auto Sync
- 30-second reconciliation
- New file detection
- Modified file detection
- Directory creation
- Direct file upload
- Persistent synchronization queue
- Duplicate prevention
- Retry failed uploads
- Maximum 3 automatic retries
- Activity logs
- App restart recovery
- Basic debounce
- File stability verification
- Secure credential storage
- Functional dashboard

---

25. Post-MVP Features

After the MVP is fully tested, add:

1. Remote deletion synchronization.
2. Temporary old-file backup.
3. 1-hour backup retention.
4. Automatic expired backup cleanup.
5. Rollback interface.
6. Optional file hash verification.
7. Multiple projects.
8. Multiple hosting connections.
9. Configurable reconciliation interval.
10. Upload progress.
11. Advanced diagnostics.
12. Synchronization statistics.
13. Project-level backup snapshots.
14. Advanced conflict detection.

---

26. Testing Requirements

The application must pass the following tests.

Test 1 — Initial Upload

Select project folder.

Configure hosting.

Press Sync Now.

Expected:

All files upload successfully.

---

Test 2 — Single File Change

Modify:

index.html

Expected:

Only index.html uploads.

---

Test 3 — Multiple File Changes

Modify:

- index.html
- css/style.css
- js/app.js

Expected:

Only those changed files upload.

Unchanged files must not upload.

---

Test 4 — New Folder

Create:

assets/images/

Add:

logo.png

Expected:

Remote directory is created.

logo.png uploads successfully.

---

Test 5 — Rapid AI Changes

Modify the same file multiple times rapidly.

Expected:

Avoid unnecessary repeated uploads.

Upload the final stable version.

---

Test 6 — Internet Loss

Disconnect the internet.

Modify a file.

Expected:

The file remains pending.

Restore the internet.

Expected:

Synchronization retries successfully.

---

Test 7 — App Restart

Create pending upload operations.

Close or restart the application.

Expected:

Queue state remains available.

Synchronization can continue safely.

---

Test 8 — Permission Revoked

Revoke the selected folder permission.

Expected:

Synchronization stops safely.

A clear error appears.

The user can select the folder again.

---

Test 9 — Large Project

Test with:

- Large number of files
- Nested folders
- Multiple simultaneous changes

Expected:

Application remains responsive.

UI does not freeze.

Unchanged files are not repeatedly uploaded.

---

27. Development Priority

The development order must strictly follow:

Priority 1

Folder access and permission.

Priority 2

Hosting connection and connection testing.

Priority 3

Initial full project upload.

Priority 4

Manual Sync.

Priority 5

File metadata database.

Priority 6

Persistent synchronization queue.

Priority 7

Automatic file change detection.

Priority 8

30-second reconciliation.

Priority 9

Debounce and file stability verification.

Priority 10

Retry and restart recovery.

Priority 11

Dashboard refinement.

Priority 12

Remote deletion synchronization.

Priority 13

Temporary 1-hour backup system.

Priority 14

Rollback.

Priority 15

Multiple projects and advanced features.

---

28. Final Acceptance Criteria

The application is considered successfully implemented only when this real-world scenario works:

The user selects a project folder.

The user connects their InfinityFree hosting account.

The user performs an initial synchronization.

The user enables Auto Sync.

The user gives a prompt to an AI coding tool.

The AI coding tool creates or modifies source files inside the selected project folder.

The Android application detects those changes.

The application waits until files are stable.

The changed files are added to the persistent synchronization queue.

Only required files are uploaded.

The remote directory structure is preserved.

The InfinityFree source files update successfully.

Local synchronization metadata updates.

The activity log records the result.

If an upload fails, retry and recovery logic handles it.

The user does not need to manually open the InfinityFree File Manager for every code change.

The final deliverable must be a real, functional Android application that can be built into an APK.

Do not produce a UI mockup or placeholder implementation.

All major MVP actions and buttons must be connected to functional implementation.

Build the MVP first, test it thoroughly, and only then proceed to post-MVP advanced features.

Read the complete PRD below carefully and build the entire Android application accordingly. Do not create a mockup, prototype, placeholder-only UI, or incomplete implementation. Follow the MVP development priority defined in the PRD, but implement the architecture so that all specified post-MVP features can be added cleanly. Ensure every implemented screen, button, permission flow, database operation, synchronization process, queue operation, and error-handling flow is functional and properly connected. Build the project as a real Android application that can be compiled into an APK.