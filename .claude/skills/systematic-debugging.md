---
name: systematic-debugging
description: "Use when encountering bugs or unexpected behavior. Methodical approach to identify root cause: Reproduce → Isolate → Identify → Fix → Verify."
---

# Systematic Debugging

## Core Principle

Debug methodically, not randomly. Follow a systematic process to identify and fix the root cause, not just symptoms.

## When to Use This Skill

- Encountering bugs or errors
- Unexpected behavior in application
- Tests failing
- Production issues
- User reports problems
- "It worked yesterday" situations
- Performance degradation
- Memory leaks
- Data inconsistencies

## The Iron Law

**NEVER "try random things until it works."** That's not debugging, it's luck.

Follow the systematic process:
1. **Reproduce** the bug reliably
2. **Isolate** where the problem occurs
3. **Identify** the root cause
4. **Fix** the root cause (not symptoms)
5. **Verify** the fix works and doesn't break anything else

## Why Systematic Debugging?

**Benefits:**
✅ Finds root cause, not symptoms
✅ Prevents same bug from recurring
✅ Builds understanding of the system
✅ Saves time in the long run
✅ Creates better fixes

**Random debugging:**
❌ Fixes symptoms, not causes
❌ Creates new bugs
❌ Wastes time on guesses
❌ No learning
❌ Bug comes back later

---

## The 5-Step Debugging Process

### Step 1: REPRODUCE

**Goal**: Get the bug to happen consistently

```
🔍 REPRODUCE Phase

Bug report: "Login sometimes fails"

Questions to answer:
- What are the exact steps to reproduce?
- Does it happen every time or intermittently?
- What is the expected behavior?
- What is the actual behavior?
- What error messages appear?
- What are the conditions (browser, data, state)?

Reproduction steps:
1. Navigate to /login
2. Enter email: test@example.com
3. Enter password: password123
4. Click "Login"
5. Result: 500 error (should be successful login)

Reproduction rate: 10/10 attempts ✅
```

**If you can't reproduce:**
- Not enough information
- Collect more data (logs, screenshots, steps)
- Ask user for exact reproduction steps
- Check environment differences (local vs production)

### Step 2: ISOLATE

**Goal**: Narrow down where the problem occurs

```
🎯 ISOLATE Phase

Bug reproduced ✅ Now isolating location...

Binary search approach:
- Is it frontend or backend? → Backend (checked network tab)
- Which controller? → AuthController
- Which method? → login()
- Which part of method? → Token generation

Adding debug logging:
```php
public function login(Request $request)
{
    Log::debug('Login attempt', ['email' => $request->email]);

    $credentials = $request->only('email', 'password');

    if (!Auth::attempt($credentials)) {
        Log::debug('Auth failed');
        return response()->json(['message' => 'Invalid credentials'], 401);
    }

    Log::debug('Auth successful');

    $user = Auth::user();
    Log::debug('User retrieved', ['user_id' => $user->id]);

    $token = $user->createToken('auth_token')->plainTextToken;
    Log::debug('Token created');  // ❌ Never reaches this line!

    return response()->json(['token' => $token]);
}
```

Result: Error occurs at `createToken()` line
Isolated to: Sanctum token generation
```

**Isolation techniques:**
- Binary search (comment out half the code)
- Add logging/print statements
- Use debugger breakpoints
- Check logs and error messages
- Reproduce in minimal test case

### Step 3: IDENTIFY

**Goal**: Find the root cause, not just where error occurs

```
🔬 IDENTIFY Phase

Location isolated: $user->createToken() ✅

Why is it failing?

Checking error logs:
```
ERROR: SQLSTATE[42S02]: Base table or view not found:
1146 Table 'database.personal_access_tokens' doesn't exist
```

Root cause identified: Sanctum migrations not run!

This is NOT a code bug, it's a setup issue.

Root cause: Missing database table
Why it happened: Sanctum package installed but migrations not executed

✅ Root cause found
```

**Investigation techniques:**
- Read error messages carefully (ENTIRE message)
- Check stack traces
- Examine related code
- Review recent changes (git log)
- Check documentation
- Search for similar issues
- Use debugger to inspect state
- Check database/logs

**Ask "Why?" five times:**
```
Bug: Login fails
Why? → createToken() throws error
Why? → personal_access_tokens table missing
Why? → Migrations not run
Why? → Forgot to run after installing Sanctum
Why? → No checklist for package installation

Real fix: Add package installation checklist (not just run migration)
```

### Step 4: FIX

**Goal**: Fix root cause, not symptoms

```
⚙️ FIX Phase

Root cause: Sanctum migrations not run

Bad fix (symptom): Catch exception, return error message
✅ This masks the problem
❌ Doesn't solve root cause

Good fix (root cause): Run migrations
```bash
php artisan migrate
```

✅ Table created
✅ Solves actual problem

Additional fix: Prevent recurrence
- Add to setup documentation
- Add to installation checklist
- Update CI/CD to check for pending migrations
```

**Fix strategies:**
- Fix root cause, not symptoms
- Make minimal changes
- Don't "fix" things you don't understand
- Add tests to prevent regression
- Document the fix
- Consider why it happened (process issue?)

### Step 5: VERIFY

**Goal**: Confirm fix works and doesn't break anything else

```
✅ VERIFY Phase

Fix applied: Migrations run

Verification tests:

1. Test original bug:
   - Attempt login
   - Result: ✅ Success, token returned

2. Test edge cases:
   - Wrong password: ✅ Correct 401 error
   - Missing email: ✅ Validation error
   - Already logged in: ✅ New token issued

3. Run full test suite:
   ```bash
   ./scripts/safe-test.sh vendor/bin/paratest
   ```
   Result: ✅ All 127 tests pass

4. Check for regressions:
   - Registration still works: ✅
   - Logout still works: ✅
   - Protected routes still work: ✅

5. Test in different environments:
   - Local: ✅ Works
   - Staging: ✅ Works

Fix verified ✅

Post-fix actions:
- Add test to prevent regression:
  ```php
  public function test_personal_access_tokens_table_exists()
  {
      $this->assertTrue(Schema::hasTable('personal_access_tokens'));
  }
  ```
- Update documentation
- Close related issues
```

**Verification checklist:**
- [ ] Original bug is fixed
- [ ] Edge cases work correctly
- [ ] All tests pass
- [ ] No regressions introduced
- [ ] Works in all environments
- [ ] Documented in commit message

---

## Debugging Techniques

### Technique 1: Rubber Duck Debugging

Explain the problem out loud (to a rubber duck, or colleague, or AI):

```
I'm debugging the login failure.

The user submits email and password.
The credentials are validated - that works.
Auth::attempt() succeeds - that works.
We retrieve the user - that works.
We call createToken() - THAT FAILS.

Why would createToken() fail?
Oh! It needs a database table. Do we have that table?
Let me check: php artisan migrate:status
Ah! personal_access_tokens migration is pending!
```

**Why it works**: Explaining forces you to think through each step logically.

### Technique 2: Divide and Conquer

```
Bug: API endpoint returns 500 error

Divide the request pipeline:
1. Route matched? → YES
2. Controller method called? → YES
3. Validation passed? → YES
4. Database query executed? → YES
5. Response formatted? → NO (error here)

Isolated to response formatting.

Divide response code:
1. Data retrieved? → YES
2. Transformer applied? → NO (error here)

Found: Transformer trying to access undefined property.
```

### Technique 3: Time Travel (Git Bisect)

```
Bug: Feature worked last week, broken now

Use git bisect to find breaking commit:
```bash
git bisect start
git bisect bad HEAD  # Current (broken)
git bisect good abc123  # Last known good commit

# Git checks out middle commit
# Test if bug exists
git bisect bad  # or 'good'

# Repeat until found
git bisect reset
```

Breaking commit found: Identifies exactly what change caused the bug.
```

### Technique 4: Minimal Reproduction

```
Bug: Complex API request fails

Create minimal test case:
```php
// Start with full request
$response = $this->postJson('/api/orders', [
    'user_id' => 1,
    'items' => [...100 items...],
    'shipping' => [...],
    'payment' => [...],
]);

// Remove parts until it works
$response = $this->postJson('/api/orders', [
    'user_id' => 1,
    'items' => [['id' => 1, 'qty' => 1]],  // Minimal
]);

// Still fails? Bug is not in items/shipping/payment
// Works? Bug is in one of the removed parts
// Add back one at a time to find culprit
```

### Technique 5: Diff Debugging

```
Bug: Works in production, fails in development (or vice versa)

Compare environments systematically:
1. Configuration diff
   - Environment variables
   - Feature flags
   - API endpoints

2. Data diff
   - Database schema versions
   - Seed data differences
   - User permissions

3. Code diff
   - Branch differences
   - Uncommitted changes
   - Compiled assets

4. Infrastructure diff
   - Server versions
   - Network configuration
   - SSL/TLS settings
```

### Technique 6: Fault Injection

```
Bug: Error handling doesn't work as expected

Deliberately inject faults to test behavior:
```python
# Temporarily force failure
def get_user_data(user_id):
    raise ConnectionError("Simulated DB failure")  # Force fail
    # ... normal code
```

Verify:
- Error is caught correctly
- User sees appropriate message
- System recovers gracefully
- Logs are written properly
```

---

## Common Debugging Scenarios

### Scenario 1: Intermittent Bug

```
Bug: Sometimes works, sometimes doesn't

Common causes:
- Race conditions
- Caching issues
- Different data/state
- Environment-specific
- Timing-dependent

Debugging approach:
1. Collect data on when it fails vs succeeds
2. Look for patterns (time of day, specific users, data)
3. Check for async operations
4. Check for cached data
5. Add extensive logging
6. Try to make it consistent (always fail or always work)
```

### Scenario 2: "Works on My Machine"

```
Bug: Works locally, fails in production

Common causes:
- Environment differences (.env)
- Missing dependencies
- Different PHP/Node versions
- Cache differences
- Database data differences
- File permissions

Debugging approach:
1. Compare .env files (sanitized)
2. Check versions (php -v, node -v)
3. Check installed packages (composer.lock, package-lock.json)
4. Check file permissions
5. Compare database schemas
6. Check logs on production
7. Try to reproduce locally with production-like setup
```

### Scenario 3: The Bug That Makes No Sense

```
Bug: Completely illogical behavior

Example: "Deleting user A deletes user B"

Debugging approach:
1. Question your assumptions
   - Maybe you're looking at wrong user?
   - Maybe there's a relationship you don't know about?
   - Maybe code path is different than you think?

2. Add logging EVERYWHERE
   - Log inputs
   - Log outputs
   - Log every step

3. Use debugger with breakpoints
   - Step through line by line
   - Inspect all variables
   - Check actual vs expected values

4. Check for global state/side effects
   - Static variables
   - Singleton patterns
   - Database triggers
   - Event listeners

Usually reveals: Misunderstood code flow or hidden side effects
```

### Scenario 4: Memory Leak

```
Bug: Application slows down over time / OOM errors

Symptoms:
- Gradual memory increase
- Performance degradation
- OutOfMemoryError eventually

Debugging approach:
1. Monitor memory over time
   - Tools: top, htop, memory profilers

2. Take heap snapshots at intervals
   - Compare object counts between snapshots
   - Look for growing collections

3. Common causes:
   - Event listeners not removed
   - Caches without limits
   - Circular references
   - Static collections accumulating data
   - Database connections not closed

4. Isolation:
   - Comment out sections
   - Run with minimal code paths
   - Identify which operation causes growth

Example (Python):
```python
import tracemalloc

tracemalloc.start()
# ... run suspicious code ...
snapshot = tracemalloc.take_snapshot()
top_stats = snapshot.statistics('lineno')
for stat in top_stats[:10]:
    print(stat)
```
```

### Scenario 5: Performance Degradation

```
Bug: Feature became slow

Debugging approach:
1. Establish baseline
   - What was the acceptable performance?
   - When did it become slow?

2. Profile the code
   - Database: EXPLAIN queries
   - Code: Profiler (Xdebug, cProfile)
   - Network: DevTools, Charles

3. Identify hotspots
   - N+1 query problems
   - Missing indexes
   - Inefficient algorithms
   - External API latency

4. Common culprits:
   - Recent data growth
   - New features without optimization
   - Missing indexes on new columns
   - Synchronous API calls that should be async

5. Measure → Optimize → Measure
   - Never optimize without measuring first
   - Verify improvement after change
```

### Scenario 6: Concurrency/Deadlock

```
Bug: System freezes under load

Symptoms:
- Works with single user
- Freezes with multiple users
- Database timeouts

Debugging approach:
1. Check for lock contention
   - Database: SHOW PROCESSLIST, pg_locks
   - Application: Thread dumps

2. Look for patterns:
   - Two resources locked in different orders
   - Long-running transactions
   - Missing transaction isolation

3. Common fixes:
   - Always lock resources in same order
   - Reduce transaction scope
   - Add timeout handling
   - Use optimistic locking

4. Test with load:
   - Simulate concurrent users
   - Use load testing tools (k6, JMeter)
```

### Scenario 7: Data Corruption

```
Bug: Data is wrong/inconsistent

Debugging approach:
1. When did corruption start?
   - Check audit logs
   - Review recent deployments
   - Look at data change timestamps

2. What's the scope?
   - Single record or multiple?
   - One table or relationships?
   - Pattern in affected data?

3. Potential causes:
   - Race conditions in updates
   - Missing transaction boundaries
   - Bug in data migration
   - External system integration error
   - Direct database manipulation

4. Recovery:
   - Restore from backup (if recent)
   - Write migration to fix data
   - Add constraints to prevent recurrence
```

### Scenario 8: Third-Party Integration Failure

```
Bug: External API integration stopped working

Debugging approach:
1. Isolate: Is it our code or their service?
   - Test API directly (Postman, curl)
   - Check their status page
   - Compare with working example

2. Check for changes:
   - API version updates
   - Authentication changes
   - Rate limiting
   - IP whitelist issues

3. Inspect the actual request/response:
   ```bash
   curl -v https://api.example.com/endpoint \
     -H "Authorization: Bearer $TOKEN" \
     -d '{"test": "data"}'
   ```

4. Common issues:
   - Expired credentials
   - SSL certificate changes
   - Payload format changes
   - New required fields
```

---

## Debugging Tools

### General Purpose Tools

#### Tool 1: Debugger (Xdebug, VS Code Debugger)

```
Benefits:
- Step through code line by line
- Inspect variables at any point
- Set breakpoints
- See call stack

Usage:
1. Set breakpoint
2. Trigger code path
3. Inspect state
4. Step through execution
```

#### Tool 2: Logging

```php
// Strategic logging
Log::debug('Step 1: Starting process', ['user_id' => $userId]);

$result = $this->processData($data);
Log::debug('Step 2: Data processed', ['result' => $result]);

if ($result->isValid()) {
    Log::debug('Step 3: Validation passed');
} else {
    Log::debug('Step 3: Validation failed', ['errors' => $result->errors()]);
}

// View logs
tail -f storage/logs/laravel.log
```

#### Tool 3: dd() / dump() (Laravel)

```php
// Dump and die (stops execution)
dd($user);

// Dump without stopping
dump($user);
dump($request->all());
dump($query->toSql());
```

#### Tool 4: Browser DevTools

```
Network tab:
- Check request/response
- Check status codes
- Check headers
- Check payload

Console tab:
- Check JavaScript errors
- Check console.log output

Sources tab:
- Set JavaScript breakpoints
- Step through JS execution
```

### APM & Monitoring Tools

```
Application Performance Monitoring:
- Sentry: Error tracking with stack traces
- New Relic: Full application monitoring
- Datadog: Infrastructure + APM
- Bugsnag: Error monitoring

Benefits:
- Real-time error alerts
- Stack traces from production
- Performance metrics
- User context (who experienced the bug)
- Release tracking (which deploy introduced it)

Setup example (Sentry):
```python
import sentry_sdk

sentry_sdk.init(
    dsn="https://xxx@sentry.io/xxx",
    traces_sample_rate=1.0,
)
```
```

### Database Debugging Tools

```
Query Analysis:
- EXPLAIN: Show query execution plan
- EXPLAIN ANALYZE: Execute and show actual times
- Query logs: See all executed queries

Example:
```sql
EXPLAIN ANALYZE
SELECT * FROM users
WHERE email = 'test@example.com';

-- Output shows:
-- Seq Scan vs Index Scan
-- Actual rows vs estimated
-- Execution time
```

Missing index detection:
```sql
-- PostgreSQL
SELECT schemaname, tablename, indexname
FROM pg_indexes
WHERE tablename = 'users';

-- Check for slow queries
SELECT query, calls, mean_time
FROM pg_stat_statements
ORDER BY mean_time DESC
LIMIT 10;
```
```

### Network Debugging Tools

```
curl - Command line HTTP:
```bash
# Verbose output
curl -v https://api.example.com/endpoint

# With timing info
curl -w "@curl-format.txt" -o /dev/null -s https://api.example.com

# Post with data
curl -X POST -d '{"key":"value"}' -H "Content-Type: application/json" \
  https://api.example.com/endpoint
```

Postman/Insomnia:
- Save and organize API requests
- Test different scenarios
- Share with team

Charles/Proxyman:
- Intercept mobile app traffic
- Modify requests/responses
- SSL proxying for HTTPS
```

---

## Android/Kotlin Specific Debugging

### Logcat Logging

```kotlin
import android.util.Log

Log.d("TAG", "Debug message")
Log.e("TAG", "Error message", exception)
Log.w("TAG", "Warning message")
Log.i("TAG", "Info message")
Log.v("TAG", "Verbose message")

// Filtering logcat
adb logcat -s TAG:D  // Only show TAG at Debug level
adb logcat *:E       // Only errors
adb logcat | grep "pattern"
```

### AppLogger (Project-Specific)

```kotlin
// Use the project's AppLogger for consistent logging
import com.etfmonitor.core.common.util.AppLogger

AppLogger.d("TAG", "Debug message")
AppLogger.e("TAG", "Error message", exception)
```

### Timber (Better Logging)

```kotlin
Timber.d("User ID: %s", userId)
Timber.e(exception, "Failed to load")
Timber.tag("CustomTag").d("Tagged message")
```

### Debug Build Checks

```kotlin
if (BuildConfig.DEBUG) {
    // Debug-only code
    Log.d("DEBUG", "Detailed state: $state")
}
```

### Compose Debugging

```kotlin
@Composable
fun MyComposable() {
    // Log recompositions
    SideEffect {
        Log.d("Recompose", "MyComposable recomposed")
    }

    // Track state changes
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state) {
        Log.d("State", "State changed to: $state")
    }
}
```

### Android Studio Tools

- **Layout Inspector**: View Compose/View hierarchy
- **Network Inspector**: HTTP traffic analysis
- **Memory Profiler**: Heap analysis, leak detection
- **CPU Profiler**: Performance bottlenecks
- **Database Inspector**: Room/SQLite queries and data
- **App Inspection**: Background tasks, workers

### Room Database Debugging

```kotlin
// Enable query logging
Room.databaseBuilder(context, AppDatabase::class.java, "db")
    .setQueryCallback({ sqlQuery, bindArgs ->
        Log.d("Room", "Query: $sqlQuery Args: $bindArgs")
    }, Executors.newSingleThreadExecutor())
    .build()
```

### Coroutine Debugging

```kotlin
// Add debug name to coroutines
viewModelScope.launch(CoroutineName("LoadData")) {
    // ... code
}

// Enable debug mode for coroutines
System.setProperty("kotlinx.coroutines.debug", "on")
```

---

## Templates

### Bug Report Template

```markdown
## Bug Report

### Title
[Short descriptive title]

### Environment
- Device: [e.g., Pixel 8, Samsung Galaxy S24]
- Android Version: [e.g., Android 14]
- App Version: [e.g., 1.2.3]

### Description
[Clear description of the bug]

### Steps to Reproduce
1. [First step]
2. [Second step]
3. [Third step]

### Expected Behavior
[What should happen]

### Actual Behavior
[What actually happens]

### Error Messages / Logs
```
[Paste any error messages or logcat output]
```

### Screenshots/Videos
[Attach if helpful]

### Additional Context
- First noticed: [date]
- Frequency: [always/sometimes/once]
- Related issues: [links]
```

### Debugging Session Log Template

```markdown
## Debugging Session Log

**Date**: YYYY-MM-DD
**Bug**: [Brief description]
**Reported by**: [Name/ticket #]

### REPRODUCE
- [ ] Reproduction steps documented
- [ ] Reproduction rate: _/10

Steps:
1.
2.
3.

### ISOLATE
- [ ] Location identified

Investigation notes:
- Checked:
- Found:
- Isolated to:

### IDENTIFY
- [ ] Root cause found

Root cause:

5 Whys:
1. Why? →
2. Why? →
3. Why? →
4. Why? →
5. Why? →

### FIX
- [ ] Fix implemented
- [ ] Tests added

Fix description:

Changed files:
-
-

### VERIFY
- [ ] Original bug fixed
- [ ] Edge cases tested
- [ ] Full test suite passes
- [ ] No regressions

Verification notes:
-
-

### POST-FIX
- [ ] Documentation updated
- [ ] Team notified
- [ ] Monitoring added (if needed)

Time spent:
Commit:
```

---

## Red Flags (Bad Debugging)

- ❌ Trying random changes without understanding
- ❌ "Let me just restart and hope it works"
- ❌ Skipping reproduction step
- ❌ Fixing symptoms instead of root cause
- ❌ Not verifying the fix
- ❌ Making multiple changes at once
- ❌ Not documenting what you tried
- ❌ Ignoring error messages (reading only part of them)
- ❌ Not checking logs
- ❌ Assuming you know the problem without investigation

## Common Rationalizations to Reject

- ❌ "It's probably just X, let me try fixing that" → Verify first
- ❌ "I don't have time for systematic debugging" → Random debugging takes longer
- ❌ "The bug is obvious" → Still verify
- ❌ "I'll just rewrite this part" → Understand before rewriting
- ❌ "It's working now, good enough" → Understand WHY it's working
- ❌ "It must be a library bug" → Almost always your code

---

## Your Commitment

When debugging:
- [ ] I will reproduce the bug reliably first
- [ ] I will isolate where the problem occurs
- [ ] I will identify the root cause, not just symptoms
- [ ] I will fix the root cause
- [ ] I will verify the fix completely
- [ ] I will add tests to prevent regression
- [ ] I will document the debugging process for complex bugs

---

**Bottom Line**: Systematic debugging finds root causes quickly. Random debugging wastes time and creates more bugs. Follow the process: Reproduce → Isolate → Identify → Fix → Verify.
