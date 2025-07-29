package org.embeddedt.modernfix.dynamicresources;

import it.unimi.dsi.fastutil.Function;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.client.resources.model.BakedModel;
import java.util.concurrent.locks.StampedLock;
import java.lang.ref.Cleaner;

public final class DynamicModelCache<K> {
    private final Reference2ReferenceOpenHashMap<K, BakedModel> cache;
    private final StampedLock lock = new StampedLock();
    private final Function<K, BakedModel> modelRetriever;
    private final boolean allowNulls;
    private static final int MAX_SIZE = 1000;
    private static final Cleaner CLEANER = Cleaner.create();

    public DynamicModelCache(Function<K, BakedModel> modelRetriever, boolean allowNulls) {
        this.modelRetriever = modelRetriever;
        this.allowNulls = allowNulls;
        this.cache = new Reference2ReferenceOpenHashMap<>(Math.min(MAX_SIZE, 512));
        CLEANER.register(this, new CacheCleaner(cache));
    }

    public void clear() {
        long stamp = lock.writeLock();
        try {
            cache.clear();
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public BakedModel get(K key) {
        if (key == null) return null;

        long stamp = lock.tryOptimisticRead();
        BakedModel model = cache.get(key);
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                model = cache.get(key);
            } finally {
                lock.unlockRead(stamp);
            }
        }

        if (model == null) {
            BakedModel loadedModel = modelRetriever.apply(key);
            
            if (loadedModel != null || allowNulls) {
                long writeStamp = lock.writeLock();
                try {
                    BakedModel existing = cache.get(key);
                    if (existing != null) return existing;

                    if (cache.size() >= MAX_SIZE) {
                        cache.remove(cache.keySet().iterator().next());
                    }

                    if (loadedModel != null || allowNulls) {
                        cache.put(key, loadedModel);
                    }
                    model = loadedModel;
                } finally {
                    lock.unlockWrite(writeStamp);
                }
            }
        }

        return model;
    }

    private static final class CacheCleaner implements Runnable {
        private final Reference2ReferenceOpenHashMap<?, ?> mapToClear;

        CacheCleaner(Reference2ReferenceOpenHashMap<?, ?> map) {
            this.mapToClear = map;
        }

        @Override
        public void run() {
            synchronized (mapToClear) {
                mapToClear.clear();
            }
        }
    }
}
