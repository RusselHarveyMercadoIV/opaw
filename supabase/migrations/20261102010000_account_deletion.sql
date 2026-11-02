-- In-app account deletion (App Store Review Guideline 5.1.1(v)).
-- Deleting the auth user cascades to profiles, dogs, posts, likes, follows, blocks, reports and avatar jobs.
-- The app removes the user's storage files first, through the Storage API.
create or replace function public.delete_my_account()
returns void
language sql
security definer
set search_path = public
as $$
    delete from auth.users where id = auth.uid();
$$;

revoke execute on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
