-- Backing up a pet is an upsert (POST with resolution=merge-duplicates), and Postgres checks the
-- SELECT policies against the new row for INSERT ... ON CONFLICT. "Members see the pet" asks
-- pet_role(id), which looks the row up in shared_pets, where the row being inserted isn't visible
-- yet, so every first backup failed with 403 and no pet was ever saved to an account.
-- Owners can always see their own pets, judged from the row itself.
create policy "Owners see their pets" on public.shared_pets for select to authenticated
    using (owner_id = (select auth.uid()));
