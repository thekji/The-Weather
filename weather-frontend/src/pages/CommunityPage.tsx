import { useEffect, useState } from 'react'
import { getCommunityPosts } from '../components/community-post/service/postService'
import type { CommunityPost } from '../components/community-post/types'
import { CommunityPostCard } from '../components/community-post/ui/CommunityPostCard'
import { PageFooterNote } from '../components/PageFooterNote'
import { useAuthStore } from '../stores/authStore'

export function CommunityPage() {
    const currentUser = useAuthStore((state) => state.user)
    const [posts, setPosts] = useState<CommunityPost[]>([])
    const [cursor, setCursor] = useState<string | null>(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')

    async function load(next?: string, signal?: AbortSignal) {
        setLoading(true); setError('')
        try {
            const page = await getCommunityPosts(next, signal)
            setPosts((current) => next ? [...current, ...page.items] : page.items)
            setCursor(page.nextCursor)
        } catch (cause) {
            if (!signal?.aborted) setError(cause instanceof Error ? cause.message : 'The community feed could not be loaded.')
        } finally { if (!signal?.aborted) setLoading(false) }
    }

    useEffect(() => {
        const controller = new AbortController()
        void getCommunityPosts(undefined, controller.signal)
            .then((page) => { setPosts(page.items); setCursor(page.nextCursor) })
            .catch((cause) => { if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : 'The community feed could not be loaded.') })
            .finally(() => { if (!controller.signal.aborted) setLoading(false) })
        return () => controller.abort()
    }, [])

    return (
        <section className="relative z-[1] mx-auto flex h-full min-h-0 w-full max-w-[1600px] flex-col animate-fade-in max-[760px]:h-auto" aria-labelledby="community-title">
            <header className="mb-[clamp(20px,3.5vh,34px)] flex shrink-0 items-start justify-between gap-7 max-[760px]:mb-4 max-[430px]:block">
                <div className="min-w-0 flex-1">
                    <p className="mt-0 mb-[9px] w-fit text-xs font-extrabold tracking-[0.14em] text-[var(--eyebrow)] uppercase max-[760px]:mb-1.5 max-[760px]:text-[0.68rem]">Weather observations</p>
                    <div className="flex items-center gap-[18px] max-[760px]:flex-wrap max-[760px]:gap-3">
                        <h1 className="m-0 w-fit text-[clamp(2.25rem,4vw,3.75rem)] leading-[0.98] font-extrabold tracking-[0.01em] text-[var(--ink)] [@media(max-height:720px)_and_(min-width:761px)]:text-[clamp(2.25rem,6vh,3.35rem)] max-[760px]:text-[clamp(2rem,9vw,2.65rem)]" id="community-title">Community</h1>
                    </div>
                    <p className="mt-[15px] mb-0 text-base leading-[1.6] italic font-medium text-[var(--muted)] max-[760px]:mt-2.5 max-[760px]:text-[0.88rem] max-[760px]:leading-[1.45]">See posts and weather updates shared by community members.</p>
                </div>
            </header>

            <div className="min-h-0 flex-1 overflow-x-hidden overflow-y-auto overscroll-contain pr-1 max-[760px]:flex-none max-[760px]:overflow-visible max-[760px]:pr-0">
                {error && <p className="rounded-xl bg-red-50 border border-red-200 p-3 text-sm font-semibold text-red-600" role="alert">{error}</p>}
                <div className="grid gap-4 max-w-[1000px]">{posts.map((post) => <CommunityPostCard post={post} currentUserID={currentUser?.userID} currentUsername={currentUser?.name} showLocation onDeleted={(postID) => setPosts((current) => current.filter((item) => item.postID !== postID))} key={post.postID} />)}</div>
                {!loading && posts.length === 0 && <p className="text-[var(--muted)] text-sm font-medium">No community observations yet.</p>}
                {loading && <p className="text-[var(--muted)] text-sm font-medium" role="status">Loading community posts…</p>}
                {cursor && <button className="mt-5 rounded-full border-0 bg-[var(--action-background)] hover:bg-[var(--action-hover-background)] px-6 py-2.5 text-xs font-bold text-[var(--action-ink)] shadow-sm transition-colors cursor-pointer" disabled={loading} onClick={() => void load(cursor)}>{loading ? 'Loading more…' : 'Load more'}</button>}
            </div>
            <PageFooterNote />
        </section>
    )
}
