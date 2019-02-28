<div>
  <h4>Add Event</h4>
  <div class="col-md-4 col-sm-8">
    <form method="post" action="{{ route('event-store') }}" class="form-horizontal">
      <div class="form-group">
        <label>Date:</label>
        <input class="form-control form-datetime" name="date" readonly>
      </div>
      <div class="form-group">
        <label>Location:</label>
        <select class="form-control" name="locationId">
          @foreach($locations as $location)
          <option value="{{$location->id}}">{{$location->name}}</option>
          @endforeach
        </select>
      </div>
      <div class="form-group">
        <label>Event Type:</label>
        <select class="form-control" name="eventTypeId">
          @foreach($eventTypes as $eventType)
          <option value="{{$eventType->id}}">{{$eventType->name}}</option>
          @endforeach
        </select>
      </div>
      <button type="submit" class="btn btn-primary">Submit</button>
    </form>
  </div>
</div>