<div class="card">
    <div class="card-body">
        <div class="card-text">
            <div>
                <strong>User:</strong> {{request()->user()->name}}
            </div>
            <div>
                <strong>Location:</strong> {{request()->user()->department ? request()->user()->department->location->locationName : ''}}
            </div>
            <div>
                <strong>Role:</strong> {{request()->user()->department ? request()->user()->department->role->role : ''}} {{request()->user()->isAdmin() ? '(Admin)':''}}
            </div>
            <hr/>
            <h5 class="card-title">Data Entry</h5>
            <div><a href="{{route('searchform-donor')}}">Donors</a></div>
            @role('find_contribution')
            <div><a href="{{ route('searchform-donation') }}">Find Contribution</a></div>
            @endrole
            <hr/>

            <h5 class="card-title">Batch Processing</h5>
            <div><a href="{{ route('create-batch') }}">Create New Batch</a></div>
            <div><a href="{{ route('receive-batch') }}">Receive Batch</a></div>
            <div><a href="{{ route('list-batches') }}">My Batches in Transit</a></div>
            @role('deposit_to_bank')
            <div><a href="{{ route('list-deposited-batches') }}">My Deposits</a></div>
            @endrole
            <hr/>

            @anyrole('reports_collection reports_returned_check reports_deleted_donation_receipts reports_with_individuals reports_big_change reports_deposits reports_summary')
            <h5 class="card-title">Reports</h5>
            @role('reports_collection')
            <div><a href="{{ route('collections-report-form') }}">Collection</a></div>
            @endrole
            @role('reports_returned_check')
            <div><a href="{{ route('returned-donation-form') }}">Returned Check</a></div>
            @endrole
            @role('reports_deleted_donation_receipts')
            <div><a href="{{ route('deleted-donation-form') }}">Deleted Donation Receipts</a></div>
            @endrole
            @role('reports_batches_in_transit')
            <div><a href="{{ route('sent-batch-form') }}">Donations In Transit</a></div>
            @endrole
            @role('reports_with_individuals')
            <div><a href="{{ route('donations-with-individuals-form') }}">Donations With Individuals</a></div>
            @endrole
            @role('reports_big_change')
            <div><a href="{{ route('big-amount-change-form') }}">Donations with Big Change</a></div>
            @endrole
            @role('reports_deposits')
            <div><a href="{{ route('deposits-form') }}">Deposited Donations</a></div>
            @endrole
            @role('reports_summary')
            <div><a href="{{ route('summary-form') }}">Campaign Summary</a></div>
            @endrole
            <hr/>
            @endanyrole

            @anyrole("read_user read_target read_location read_permission")
            <h5 class="card-title">Administration</h5>

            @role('read_user')
            <div><a href="{{ route('all-users') }}">Users</a></div>
            @endrole

            @role('read_location')
            <div><a href="{{ route('all-locations') }}">Locations</a></div>
            @endrole

            @role('read_target')
            <div><a href="{{ route('all-targets') }}">Targets</a></div>
            @endrole

            @role('admin')
            <div><a href="{{ route('all-userroles') }}">User Roles</a></div>
            @endrole
            @role('read_permission')
            <div><a href="{{ route('all-permissions') }}">Permissions</a></div>
            @endrole

            @role('admin')
            <div><a href="{{ route('all-locationroles') }}">Department</a></div>
            <div><a href="{{ route('all-shipmentmethods') }}">Shipment Methods</a></div>
            <div><a href="{{ route('all-status') }}">Statuses</a></div>
            <div><a href="{{ route('all-configs') }}">App Config Override</a></div>
            @endrole
            <hr/>
            @endanyrole
            <h5 class="card-title">Session</h5>
            <div><a href="{{ route('self-user') }}">Profile</a></div>
            <div><a href="#" onclick="$('#frmLogout').submit();">Logout</a></div>
        </div>
    </div>
</div>
