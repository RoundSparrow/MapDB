/*
 *  Copyright (c) 2012 Jan Kotek
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.mapdb;


import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * A builder class for creating and opening a database.
 *
 * @author Jan Kotek
 */
public class DBMaker{

    protected static final Logger LOG = Logger.getLogger(DBMaker.class.getName());

    protected final String TRUE = "true";

    protected Fun.RecordCondition cacheCondition;
    protected ScheduledExecutorService executor;

    protected interface Keys{
        String cache = "cache";
        String cacheSize = "cacheSize";
        String cache_disable = "disable";
        String cache_hashTable = "hashTable";
        String cache_hardRef = "hardRef";
        String cache_softRef = "softRef";
        String cache_weakRef = "weakRef";
        String cache_lru = "lru";

        String file = "file";

        String volume = "volume";
        String volume_raf = "raf";
        String volume_mmapfIfSupported = "mmapfIfSupported";
        String volume_mmapf = "mmapf";
        String volume_byteBuffer = "byteBuffer";
        String volume_directByteBuffer = "directByteBuffer";
        String volume_unsafe = "unsafe";


        String lockScale = "lockScale";

        String lock = "lock";
        String lock_readWrite = "readWrite";
        String lock_single = "single";
        String lock_threadUnsafe = "threadUnsafe";

        String store = "store";
        String store_direct = "direct";
        String store_wal = "wal";
        String store_append = "append";
        String store_heap = "heap";

        String transactionDisable = "transactionDisable";

        String asyncWrite = "asyncWrite";
        String asyncWriteFlushDelay = "asyncWriteFlushDelay";
        String asyncWriteQueueSize = "asyncWriteQueueSize";

        String deleteFilesAfterClose = "deleteFilesAfterClose";
        String closeOnJvmShutdown = "closeOnJvmShutdown";

        String readOnly = "readOnly";

        String compression = "compression";
        String compression_lzf = "lzf";

        String encryptionKey = "encryptionKey";
        String encryption = "encryption";
        String encryption_xtea = "xtea";

        String checksum = "checksum";

        String freeSpaceReclaimQ = "freeSpaceReclaimQ";
        String commitFileSyncDisable = "commitFileSyncDisable";

        String snapshots = "snapshots";

        String strictDBGet = "strictDBGet";

        String fullTx = "fullTx";
    }

    protected Properties props = new Properties();

    /** use static factory methods, or make subclass */
    protected DBMaker(){}

    protected DBMaker(File file) {
        props.setProperty(Keys.file, file.getPath());
    }

    /**
     * Creates new in-memory database which stores all data on heap without serialization.
     * This mode should be very fast, but data will affect Garbage Collector the same way as traditional Java Collections.
     */
    public static DBMaker newHeapDB(){
        return new DBMaker()._newHeapDB();
    }

    public DBMaker _newHeapDB(){
        props.setProperty(Keys.store,Keys.store_heap);
        return this;
    }


    /** Creates new in-memory database. Changes are lost after JVM exits.
     * <p>
     * This will use HEAP memory so Garbage Collector is affected.
     */
    public static DBMaker newMemoryDB(){
        return new DBMaker()._newMemoryDB();
    }

    public DBMaker _newMemoryDB(){
        props.setProperty(Keys.volume,Keys.volume_byteBuffer);
        return this;
    }

    /** Creates new in-memory database. Changes are lost after JVM exits.
     * <p>
     * This will use DirectByteBuffer outside of HEAP, so Garbage Collector is not affected
     */
    public static DBMaker newMemoryDirectDB(){
        return new DBMaker()._newMemoryDirectDB();
    }

    public  DBMaker _newMemoryDirectDB() {
        props.setProperty(Keys.volume,Keys.volume_directByteBuffer);
        return this;
    }


    /** Creates new in-memory database. Changes are lost after JVM exits.
     * <p>
     * This will use {@code sun.misc.Unsafe}. It uses direct-memory access and avoids boundary checking.
     * It is bit faster compared to {@code DirectByteBuffer}, but can cause JVM crash in case of error.
     * <p>
     * If {@code sun.misc.Unsafe} is not available for some reason, MapDB will log an warning and fallback into
     * {@code DirectByteBuffer} based in-memory store without throwing an exception.
     */
    public static DBMaker newMemoryUnsafeDB(){
        return new DBMaker()._newMemoryUnsafeDB();
    }

    public  DBMaker _newMemoryUnsafeDB() {
        props.setProperty(Keys.volume,Keys.volume_unsafe);
        return this;
    }



    /**
     * Creates or open append-only database stored in file.
     * This database uses format other than usual file db
     *
     * @param file
     * @return maker
     */
    public static DBMaker newAppendFileDB(File file) {
        return new DBMaker()._newAppendFileDB(file);
    }

    public DBMaker _newAppendFileDB(File file) {
        props.setProperty(Keys.file, file.getPath());
        props.setProperty(Keys.store, Keys.store_append);
        return this;
    }


    /**
     * Create new BTreeMap backed by temporary file storage.
     * This is quick way to create 'throw away' collection.
     *
     * <p>Storage is created in temp folder and deleted on JVM shutdown
     */
    public static <K,V> BTreeMap<K,V> newTempTreeMap(){
        return newTempFileDB()
                .deleteFilesAfterClose()
                .closeOnJvmShutdown()
                .transactionDisable()
                .make()
                .getTreeMap("temp");
    }

    /**
     * Create new HTreeMap backed by temporary file storage.
     * This is quick way to create 'throw away' collection.
     *
     * <p>Storage is created in temp folder and deleted on JVM shutdown
     */
    public static <K,V> HTreeMap<K,V> newTempHashMap(){
        return newTempFileDB()
                .deleteFilesAfterClose()
                .closeOnJvmShutdown()
                .transactionDisable()
                .make()
                .getHashMap("temp");
    }

    /**
     * Create new TreeSet backed by temporary file storage.
     * This is quick way to create 'throw away' collection.
     *
     * <p>Storage is created in temp folder and deleted on JVM shutdown
     */
    public static <K> NavigableSet<K> newTempTreeSet(){
        return newTempFileDB()
                .deleteFilesAfterClose()
                .closeOnJvmShutdown()
                .transactionDisable()
                .make()
                .getTreeSet("temp");
    }

    /**
     * Create new HashSet backed by temporary file storage.
     * This is quick way to create 'throw away' collection.
     * <p>
     * Storage is created in temp folder and deleted on JVM shutdown
     */
    public static <K> Set<K> newTempHashSet(){
        return newTempFileDB()
                .deleteFilesAfterClose()
                .closeOnJvmShutdown()
                .transactionDisable()
                .make()
                .getHashSet("temp");
    }

    /**
     * Creates new database in temporary folder.
     */
    public static DBMaker newTempFileDB() {
        try {
            return newFileDB(File.createTempFile("mapdb-temp","db"));
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    /**
     * Creates new off-heap cache with maximal size in GBs.
     * Entries are removed from cache in most-recently-used fashion
     * if store becomes too big.
     *
     * This method uses off-heap direct ByteBuffers. See {@link java.nio.ByteBuffer#allocateDirect(int)}
     *
     * @param size maximal size of off-heap store in gigabytes.
     * @return map
     */
    public static <K,V> HTreeMap<K,V> newCacheDirect(double size){
        return DBMaker
                .newMemoryDirectDB()
                .transactionDisable()
                .make()
                .createHashMap("cache")
                .expireStoreSize(size)
                .counterEnable()
                .make();
    }

    /**
     * Creates new cache with maximal size in GBs.
     * Entries are removed from cache in most-recently-used fashion
     * if store becomes too big.
     *
     * This cache uses on-heap `byte[]`, but does not affect GC since objects are serialized into binary form.
     * This method uses  ByteBuffers backed by on-heap byte[]. See {@link java.nio.ByteBuffer#allocate(int)}
     *
     * @param size maximal size of off-heap store in gigabytes.
     * @return map
     */
    public static <K,V> HTreeMap<K,V> newCache(double size){
        return DBMaker
                .newMemoryDB()
                .transactionDisable()
                .make()
                .createHashMap("cache")
                .expireStoreSize(size)
                .counterEnable()
                .make();
    }


    /** Creates or open database stored in file. */
    public static DBMaker newFileDB(File file){
        return new DBMaker(file);
    }

    public DBMaker _newFileDB(File file){
        props.setProperty(Keys.file, file.getPath());
        return this;
    }


    /**
     * Transaction journal is enabled by default
     * You must call <b>DB.commit()</b> to save your changes.
     * It is possible to disable transaction journal for better write performance
     * In this case all integrity checks are sacrificed for faster speed.
     * <p>
     * If transaction journal is disabled, all changes are written DIRECTLY into store.
     * You must call DB.close() method before exit,
     * otherwise your store <b>WILL BE CORRUPTED</b>
     *
     *
     * @return this builder
     */
    public DBMaker transactionDisable(){
        props.put(Keys.transactionDisable,TRUE);
        return this;
    }

    /**
     * Install callback condition, which decides if some record is to be included in cache.
     * Condition should return `true` for every record which should be included
     *
     * This could be for example useful to include only BTree Directory Nodes and leave values and Leaf nodes outside of cache.
     *
     * !!! Warning:!!!
     *
     * Cache requires **consistent** true or false. Failing to do so will result in inconsitent cache and possible data corruption.

     * Condition is also executed several times, so it must be very fast
     *
     * You should only use very simple logic such as `value instanceof SomeClass`.
     *
     * @return this builder
     */
    public DBMaker cacheCondition(Fun.RecordCondition cacheCondition){
        this.cacheCondition = cacheCondition;
        return this;
    }

    /**

    /**
     * Instance cache is enabled by default.
     * This greatly decreases serialization overhead and improves performance.
     * Call this method to disable instance cache, so an object will always be deserialized.
     * <p>
     * This may workaround some problems
     *
     * @return this builder
     */
    public DBMaker cacheDisable(){
        props.put(Keys.cache,Keys.cache_disable);
        return this;
    }

    /**
     * Enables unbounded hard reference cache.
     * This cache is good if you have lot of available memory.
     * <p>
     * All fetched records are added to HashMap and stored with hard reference.
     * To prevent OutOfMemoryExceptions MapDB monitors free memory,
     * if it is bellow 25% cache is cleared.
     *
     * @return this builder
     */
    public DBMaker cacheHardRefEnable(){
        props.put(Keys.cache,Keys.cache_hardRef);
        return this;
    }


    /**
     * Enables unbounded cache which uses <code>WeakReference</code>.
     * Items are removed from cache by Garbage Collector
     *
     * @return this builder
     */
    public DBMaker cacheWeakRefEnable(){
        props.put(Keys.cache,Keys.cache_weakRef);
        return this;
    }

    /**
     * Enables unbounded cache which uses <code>SoftReference</code>.
     * Items are removed from cache by Garbage Collector
     *
     * @return this builder
     */
    public DBMaker cacheSoftRefEnable(){
        props.put(Keys.cache,Keys.cache_softRef);
        return this;
    }

    /**
     * Enables Least Recently Used cache. It is fixed size cache and it removes less used items to make space.
     *
     * @return this builder
     */
    public DBMaker cacheLRUEnable(){
        props.put(Keys.cache,Keys.cache_lru);
        return this;
    }

    /**
     * Disable locks. This will make MapDB thread unsafe. It will also disable any background thread workers.
     * <p>
     * <b>WARNING: </b> this option is dangerous. With locks disabled multi-threaded access could cause data corruption and causes.
     * MapDB does not have fail-fast iterator or any other means of protection
     * <p>
     * @return this builder
     */
    public DBMaker lockThreadUnsafeEnable() {
        props.put(Keys.lock, Keys.lock_threadUnsafe);
        return this;
    }

    /**
     * Disables double read-write locks and enables single read-write locks.
     * <p>
     * This type of locking have smaller overhead and can be faster in mostly-write scenario.
     * <p>
     * @return this builder
     */
    public DBMaker lockSingleEnable() {
        props.put(Keys.lock, Keys.lock_single);
        return this;
    }


    /**
     * Sets concurrency scale. More locks means better scalability with multiple cores, but also higher memory overhead
     * <p>
     * This value has to be power of two, so it is rounded up automatically.
     * <p>
     * @return this builder
     */
    public DBMaker lockScale(int scale) {
        props.put(Keys.lockScale, ""+scale);
        return this;
    }



    /**
     * Enables Memory Mapped Files, much faster storage option. However on 32bit JVM this mode could corrupt
     * your DB thanks to 4GB memory addressing limit.
     *
     * You may experience `java.lang.OutOfMemoryError: Map failed` exception on 32bit JVM, if you enable this
     * mode.
     */
    public DBMaker mmapFileEnable() {
        assertNotInMemoryVolume();
        props.setProperty(Keys.volume,Keys.volume_mmapf);
        return this;
    }

    private void assertNotInMemoryVolume() {
        if(Keys.volume_byteBuffer.equals(props.getProperty(Keys.volume)) ||
           Keys.volume_directByteBuffer.equals(props.getProperty(Keys.volume)))
            throw new IllegalArgumentException("Can not enable mmap file for in-memory store");
    }

    /**
     * Enable Memory Mapped Files only if current JVM supports it (is 64bit).
     */
    public DBMaker mmapFileEnableIfSupported() {
        assertNotInMemoryVolume();
        props.setProperty(Keys.volume,Keys.volume_mmapfIfSupported);
        return this;
    }

    /**
     * Set cache size. Interpretations depends on cache type.
     * For fixed size caches (such as FixedHashTable cache) it is maximal number of items in cache.
     * <p>
     * For unbounded caches (such as HardRef cache) it is initial capacity of underlying table (HashMap).
     * <p>
     * Default cache size is 2048.
     *
     * @param cacheSize new cache size
     * @return this builder
     */
    public DBMaker cacheSize(int cacheSize){
        props.setProperty(Keys.cacheSize,""+cacheSize);
        return this;
    }

    /**
     * MapDB supports snapshots. `TxEngine` requires additional locking which has small overhead when not used.
     * Snapshots are disabled by default. This option switches the snapshots on.
     *
     * @return this builder
     */
    public DBMaker snapshotEnable(){
        props.setProperty(Keys.snapshots,TRUE);
        return this;
    }


    /**
     * Enables mode where all modifications are queued and written into disk on Background Writer Thread.
     * So all modifications are performed in asynchronous mode and do not block.
     *
     * <p>
     * Enabling this mode might increase performance for single threaded apps.
     *
     * @return this builder
     */
    public DBMaker asyncWriteEnable(){
        LOG.warning("AsyncWrite is not implemented at this moment");
        props.setProperty(Keys.asyncWrite,TRUE);
        return this;
    }



    /**
     * Set flush interval for write cache, by default is 0
     * <p>
     * When BTreeMap is constructed from ordered set, tree node size is increasing linearly with each
     * item added. Each time new key is added to tree node, its size changes and
     * storage needs to find new place. So constructing BTreeMap from ordered set leads to large
     * store fragmentation.
     * <p>
     *  Setting flush interval is workaround as BTreeMap node is always updated in memory (write cache)
     *  and only final version of node is stored on disk.
     *
     *
     * @param delay flush write cache every N miliseconds
     * @return this builder
     */
    public DBMaker asyncWriteFlushDelay(int delay){
        props.setProperty(Keys.asyncWriteFlushDelay,""+delay);
        return this;
    }

    /**
     * Set size of async Write Queue. Default size is
     * <p>
     * Using too large queue size can lead to out of memory exception.
     *
     * @param queueSize of queue
     * @return this builder
     */
    public DBMaker asyncWriteQueueSize(int queueSize){
        props.setProperty(Keys.asyncWriteQueueSize,""+queueSize);
        return this;
    }


    /**
     * Try to delete files after DB is closed.
     * File deletion may silently fail, especially on Windows where buffer needs to be unmapped file delete.
     *
     * @return this builder
     */
    public DBMaker deleteFilesAfterClose(){
        props.setProperty(Keys.deleteFilesAfterClose,TRUE);
        return this;
    }

    /**
     * Adds JVM shutdown hook and closes DB just before JVM;
     *
     * @return this builder
     */
    public DBMaker closeOnJvmShutdown(){
        props.setProperty(Keys.closeOnJvmShutdown,TRUE);
        return this;
    }

    /**
     * Enables record compression.
     * <p>
     * Make sure you enable this every time you reopen store, otherwise record de-serialization fails unpredictably.
     *
     * @return this builder
     */
    public DBMaker compressionEnable(){
        props.setProperty(Keys.compression,Keys.compression_lzf);
        return this;
    }


    /**
     * Encrypt storage using XTEA algorithm.
     * <p>
     * XTEA is sound encryption algorithm. However implementation in MapDB was not peer-reviewed.
     * MapDB only encrypts records data, so attacker may see number of records and their sizes.
     * <p>
     * Make sure you enable this every time you reopen store, otherwise record de-serialization fails unpredictably.
     *
     * @param password for encryption
     * @return this builder
     */
    public DBMaker encryptionEnable(String password){
        return encryptionEnable(password.getBytes(Charset.forName("UTF8")));
    }



    /**
     * Encrypt storage using XTEA algorithm.
     * <p>
     * XTEA is sound encryption algorithm. However implementation in MapDB was not peer-reviewed.
     * MapDB only encrypts records data, so attacker may see number of records and their sizes.
     * <p>
     * Make sure you enable this every time you reopen store, otherwise record de-serialization fails unpredictably.
     *
     * @param password for encryption
     * @return this builder
     */
    public DBMaker encryptionEnable(byte[] password){
        props.setProperty(Keys.encryption, Keys.encryption_xtea);
        props.setProperty(Keys.encryptionKey, toHexa(password));
        return this;
    }


    /**
     * Adds CRC32 checksum at end of each record to check data integrity.
     * It throws 'IOException("Checksum does not match, data broken")' on de-serialization if data are corrupted
     * <p>
     * Make sure you enable this every time you reopen store, otherwise record de-serialization fails.
     *
     * @return this builder
     */
    public DBMaker checksumEnable(){
        props.setProperty(Keys.checksum,TRUE);
        return this;
    }


    /**
     * DB Get methods such as {@link DB#getTreeMap(String)} or {@link DB#getAtomicLong(String)} auto create
     * new record with default values, if record with given name does not exist. This could be problem if you would like to enforce
     * stricter database schema. So this parameter disables record auto creation.
     *
     * If this set, `DB.getXX()` will throw an exception if given name does not exist, instead of creating new record (or collection)
     *
     * @return this builder
     */
    public DBMaker strictDBGet(){
        props.setProperty(Keys.strictDBGet,TRUE);
        return this;
    }




    /**
     * Open store in read-only mode. Any modification attempt will throw
     * <code>UnsupportedOperationException("Read-only")</code>
     *
     * @return this builder
     */
    public DBMaker readOnly(){
        props.setProperty(Keys.readOnly,TRUE);
        return this;
    }



    /**
     * Set free space reclaim Q.  It is value from 0 to 10, indicating how eagerly MapDB
     * searchs for free space inside store to reuse, before expanding store file.
     * 0 means that no free space will be reused and store file will just grow (effectively append only).
     * 10 means that MapDB tries really hard to reuse free space, even if it may hurt performance.
     * Default value is 5;
     *
     *
     * @return this builder
     */
    public DBMaker freeSpaceReclaimQ(int q){
        if(q<0||q>10) throw new IllegalArgumentException("wrong Q");
        props.setProperty(Keys.freeSpaceReclaimQ,""+q);
        return this;
    }


    /**
     * Disables file sync on commit. This way transactions are preserved (rollback works),
     * but commits are not 'durable' and data may be lost if store is not properly closed.
     * File store will get properly synced when closed.
     * Disabling this will make commits faster.
     *
     * @return this builder
     */
    public DBMaker commitFileSyncDisable(){
        props.setProperty(Keys.commitFileSyncDisable,TRUE);
        return this;
    }



    /** constructs DB using current settings */
    public DB make(){
        boolean strictGet = propsGetBool(Keys.strictDBGet);
        boolean deleteFilesAfterClose = propsGetBool(Keys.deleteFilesAfterClose);
        Engine engine = makeEngine();
        boolean dbCreated = false;
        try{
            DB db =  new  DB(engine, strictGet, deleteFilesAfterClose);
            dbCreated = true;
            return db;
        }finally {
            //did db creation fail? in that case close engine to unlock files
            if(!dbCreated)
                engine.close();
        }
    }

    
    public TxMaker makeTxMaker(){
        props.setProperty(Keys.fullTx,TRUE);
        snapshotEnable();
        Engine e = makeEngine();
        //init catalog if needed
        DB db = new DB(e);
        db.commit();
        return new TxMaker(e, propsGetBool(Keys.strictDBGet), propsGetBool(Keys.snapshots));
    }

    /** constructs Engine using current settings */
    public Engine makeEngine(){

        final boolean readOnly = propsGetBool(Keys.readOnly);
        final String file = props.containsKey(Keys.file)? props.getProperty(Keys.file):"";
        final String volume = props.getProperty(Keys.volume);
        final String store = props.getProperty(Keys.store);

        if(readOnly && file.isEmpty())
            throw new UnsupportedOperationException("Can not open in-memory DB in read-only mode.");

        if(readOnly && !new File(file).exists() && !Keys.store_append.equals(store)){
            throw new UnsupportedOperationException("Can not open non-existing file in read-only mode.");
        }


        extendArgumentCheck();


        Engine engine;
        int lockingStrategy = 0;
        String lockingStrategyStr = props.getProperty(Keys.lock,Keys.lock_readWrite);
        if(Keys.lock_single.equals(lockingStrategyStr)){
            lockingStrategy = 1;
        }else if(Keys.lock_threadUnsafe.equals(lockingStrategyStr)) {
            lockingStrategy = 2;
        }

        final int lockScale = DataIO.nextPowTwo(propsGetInt(Keys.lockScale,CC.DEFAULT_LOCK_SCALE));

        boolean cacheLockDisable = lockingStrategy!=0;

        if(Keys.store_heap.equals(store)){
            engine = new StoreHeap(propsGetBool(Keys.transactionDisable),lockScale,lockingStrategy);

        }else  if(Keys.store_append.equals(store)){
            if(Keys.volume_byteBuffer.equals(volume)||Keys.volume_directByteBuffer.equals(volume))
                throw new UnsupportedOperationException("Append Storage format is not supported with in-memory dbs");

            Fun.Function1<Volume, String> volFac = extendStoreVolumeFactory(false);
            engine = new StoreAppend(
                    file,
                    volFac,
                    createCache(cacheLockDisable,lockScale),
                    lockScale,
                    lockingStrategy,
                    propsGetBool(Keys.checksum),
                    Keys.compression_lzf.equals(props.getProperty(Keys.compression)),
                    propsGetXteaEncKey(),
                    propsGetBool(Keys.readOnly),
                    propsGetBool(Keys.transactionDisable)
            );

        }else{
            Fun.Function1<Volume, String> volFac = extendStoreVolumeFactory(false);
            boolean compressionEnabled = Keys.compression_lzf.equals(props.getProperty(Keys.compression));

            engine = propsGetBool(Keys.transactionDisable) ?

                    new StoreDirect(
                            file,
                            volFac,
                            createCache(cacheLockDisable,lockScale),
                            lockScale,
                            lockingStrategy,
                            propsGetBool(Keys.checksum),
                            compressionEnabled,
                            propsGetXteaEncKey(),
                            propsGetBool(Keys.readOnly),
                            propsGetInt(Keys.freeSpaceReclaimQ,CC.DEFAULT_FREE_SPACE_RECLAIM_Q),
                            propsGetBool(Keys.commitFileSyncDisable),
                            0):

                    new StoreWAL(
                            file,
                            volFac,
                            createCache(cacheLockDisable,lockScale),
                            lockScale,
                            lockingStrategy,
                            propsGetBool(Keys.checksum),
                            compressionEnabled,
                            propsGetXteaEncKey(),
                            propsGetBool(Keys.readOnly),
                            propsGetInt(Keys.freeSpaceReclaimQ, CC.DEFAULT_FREE_SPACE_RECLAIM_Q),
                            propsGetBool(Keys.commitFileSyncDisable),
                            0);
        }

        if(engine instanceof Store){
            ((Store)engine).init();
        }

        engine = extendWrapStore(engine);

        if(propsGetBool(Keys.asyncWrite) && !readOnly){
            engine = extendAsyncWriteEngine(engine);
        }



        if(propsGetBool(Keys.snapshots))
            engine = extendSnapshotEngine(engine, lockScale);

        engine = extendWrapSnapshotEngine(engine);

        if(readOnly)
            engine = new Engine.ReadOnly(engine);


        if(propsGetBool(Keys.closeOnJvmShutdown)){
            engine = new CloseOnJVMShutdown(engine);
        }


        //try to readrt one record from DB, to make sure encryption and compression are correctly set.
        Fun.Pair<Integer,byte[]> check = null;
        try{
            check = (Fun.Pair<Integer, byte[]>) engine.get(Engine.RECID_RECORD_CHECK, Serializer.BASIC);
            if(check!=null){
                if(check.a != Arrays.hashCode(check.b))
                    throw new RuntimeException("invalid checksum");
            }
        }catch(Throwable e){
            throw new IllegalArgumentException("Error while opening store. Make sure you have right password, compression or encryption is well configured.",e);
        }
        if(check == null && !engine.isReadOnly()){
            //new db, so insert testing record
            byte[] b = new byte[127];
            new Random().nextBytes(b);
            check = new Fun.Pair(Arrays.hashCode(b), b);
            engine.update(Engine.RECID_RECORD_CHECK, check, Serializer.BASIC);
            engine.commit();
        }


        return engine;
    }

    protected Store.Cache createCache(boolean disableLocks, int lockScale) {
        final String cache = props.getProperty(Keys.cache, CC.DEFAULT_CACHE);

        if(Keys.cache_disable.equals(cache)){
            return null;
        }else if(Keys.cache_hashTable.equals(cache)){
            int cacheSize = propsGetInt(Keys.cacheSize, CC.DEFAULT_CACHE_SIZE) / lockScale;
            return new Store.Cache.HashTable(cacheSize,disableLocks);
        }else if (Keys.cache_hardRef.equals(cache)){
            int cacheSize = propsGetInt(Keys.cacheSize, CC.DEFAULT_CACHE_SIZE) / lockScale;
            return new Store.Cache.HardRef(cacheSize,disableLocks);
        }else if (Keys.cache_weakRef.equals(cache)){
            return new Store.Cache.WeakSoftRef(true,disableLocks);
        }else if (Keys.cache_softRef.equals(cache)){
            return new Store.Cache.WeakSoftRef(false,disableLocks);
        }else if (Keys.cache_lru.equals(cache)){
            int cacheSize = propsGetInt(Keys.cacheSize, CC.DEFAULT_CACHE_SIZE) / lockScale;
            return new Store.Cache.LRU(cacheSize,disableLocks);
        }else{
            throw new IllegalArgumentException("unknown cache type: "+cache);
        }
    }


    protected int propsGetInt(String key, int defValue){
        String ret = props.getProperty(key);
        if(ret==null) return defValue;
        return Integer.valueOf(ret);
    }

    protected long propsGetLong(String key, long defValue){
        String ret = props.getProperty(key);
        if(ret==null) return defValue;
        return Long.valueOf(ret);
    }


    protected boolean propsGetBool(String key){
        String ret = props.getProperty(key);
        return ret!=null && ret.equals(TRUE);
    }

    protected byte[] propsGetXteaEncKey(){
        if(!Keys.encryption_xtea.equals(props.getProperty(Keys.encryption)))
            return null;
        return fromHexa(props.getProperty(Keys.encryptionKey));
    }

    /**
     * Check if large files can be mapped into memory.
     * For example 32bit JVM can only address 2GB and large files can not be mapped,
     * so for 32bit JVM this function returns false.
     *
     */
    protected static boolean JVMSupportsLargeMappedFiles() {
        String prop = System.getProperty("os.arch");
        if(prop!=null && prop.contains("64")) return true;
        //TODO better check for 32bit JVM
        return false;
    }


    protected int propsGetRafMode(){
        String volume = props.getProperty(Keys.volume);
        if(volume==null||Keys.volume_raf.equals(volume)){
            return 2;
        }else if(Keys.volume_mmapfIfSupported.equals(volume)){
            return JVMSupportsLargeMappedFiles()?0:2;
            //TODO clear mmap values
//        }else if(Keys.volume_mmapfPartial.equals(volume)){
//            return 1;
        }else if(Keys.volume_mmapf.equals(volume)){
            return 0;
        }
        return 2; //default option is RAF
    }


    protected Engine extendSnapshotEngine(Engine engine, int lockScale) {
        return new TxEngine(engine,propsGetBool(Keys.fullTx), lockScale);
    }

    protected Engine extendAsyncWriteEngine(Engine engine) {
        return engine;
        //TODO async write
//        return new AsyncWriteEngine(engine,
//                propsGetInt(Keys.asyncWriteFlushDelay,CC.ASYNC_WRITE_FLUSH_DELAY),
//                propsGetInt(Keys.asyncWriteQueueSize,CC.ASYNC_WRITE_QUEUE_SIZE),
//                null);
    }


    protected void extendArgumentCheck() {
    }

    protected Engine extendWrapStore(Engine engine) {
        return engine;
    }



    protected Engine extendWrapSnapshotEngine(Engine engine) {
        return engine;
    }


    protected Fun.Function1<Volume,String>  extendStoreVolumeFactory(boolean index) {
        String volume = props.getProperty(Keys.volume);
        if(Keys.volume_byteBuffer.equals(volume))
            return Volume.memoryFactory(false,CC.VOLUME_PAGE_SHIFT);
        else if(Keys.volume_directByteBuffer.equals(volume))
            return Volume.memoryFactory(true,CC.VOLUME_PAGE_SHIFT);
        else if(Keys.volume_unsafe.equals(volume))
            return Volume.memoryUnsafeFactory(CC.VOLUME_PAGE_SHIFT);

        boolean raf = propsGetRafMode()!=0;
        if(raf && index && propsGetRafMode()==1)
            raf = false;

        return Volume.fileFactory(raf, propsGetBool(Keys.readOnly),
                CC.VOLUME_PAGE_SHIFT,0);
    }

    protected static String toHexa( byte [] bb ) {
        char[] HEXA_CHARS = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
        char[] ret = new char[bb.length*2];
        for(int i=0;i<bb.length;i++){
            ret[i*2] =HEXA_CHARS[((bb[i]& 0xF0) >> 4)];
            ret[i*2+1] = HEXA_CHARS[((bb[i] & 0x0F))];
        }
        return new String(ret);
    }

    protected static byte[] fromHexa(String s ) {
        byte[] ret = new byte[s.length()/2];
        for(int i=0;i<ret.length;i++){
            ret[i] = (byte) Integer.parseInt(s.substring(i*2,i*2+2),16);
        }
        return ret;
    }

    /**
     * Closes Engine on JVM shutdown using shutdown hook: {@link Runtime#addShutdownHook(Thread)}
     * If engine was closed by user before JVM shutdown, hook is removed to save memory.
     */
    public static class CloseOnJVMShutdown implements Engine{

        final protected AtomicBoolean shutdownHappened = new AtomicBoolean(false);

        final Runnable hookRunnable = new Runnable() {
            @Override
            public void run() {
                shutdownHappened.set(true);
                CloseOnJVMShutdown.this.hook = null;
                if(CloseOnJVMShutdown.this.isClosed())
                    return;
                CloseOnJVMShutdown.this.close();
            }
        };

        protected final Engine engine;

        protected Thread hook;


        public CloseOnJVMShutdown(Engine engine) {
            this.engine = engine;
            hook = new Thread(hookRunnable,"MapDB shutdown hook");
            Runtime.getRuntime().addShutdownHook(hook);
        }

        @Override
        public long preallocate() {
            return engine.preallocate();
        }

        @Override
        public <A> long put(A value, Serializer<A> serializer) {
            return engine.put(value,serializer);
        }

        @Override
        public <A> A get(long recid, Serializer<A> serializer) {
            return engine.get(recid,serializer);
        }

        @Override
        public <A> void update(long recid, A value, Serializer<A> serializer) {
            engine.update(recid,value,serializer);
        }

        @Override
        public <A> boolean compareAndSwap(long recid, A expectedOldValue, A newValue, Serializer<A> serializer) {
            return engine.compareAndSwap(recid,expectedOldValue,newValue,serializer);
        }

        @Override
        public <A> void delete(long recid, Serializer<A> serializer) {
            engine.delete(recid,serializer);
        }

        @Override
        public void close() {
            engine.close();
            if(!shutdownHappened.get() && hook!=null){
                Runtime.getRuntime().removeShutdownHook(hook);
            }
            hook = null;
        }

        @Override
        public boolean isClosed() {
            return engine.isClosed();
        }

        @Override
        public void commit() {
            engine.commit();
        }

        @Override
        public void rollback() throws UnsupportedOperationException {
            engine.rollback();
        }

        @Override
        public boolean isReadOnly() {
            return engine.isReadOnly();
        }

        @Override
        public boolean canRollback() {
            return engine.canRollback();
        }

        @Override
        public boolean canSnapshot() {
            return engine.canSnapshot();
        }

        @Override
        public Engine snapshot() throws UnsupportedOperationException {
            return engine.snapshot();
        }

        @Override
        public Engine getWrappedEngine() {
            return engine;
        }

        @Override
        public void clearCache() {
            engine.clearCache();
        }

        @Override
        public void compact() {
            engine.compact();
        }
    }


}
